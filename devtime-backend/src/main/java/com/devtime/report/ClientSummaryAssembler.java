package com.devtime.report;

import com.devtime.contract.BalanceService;
import com.devtime.contract.ContractPeriodService;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.dto.ContractResponses.ContractPeriodResponse;
import com.devtime.contract.dto.ContractResponses.ContractReportRef;
import com.devtime.report.domain.ReportGrouping;
import com.devtime.report.domain.ReportSource;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.report.dto.ReportResponses.ClientSummaryContractSection;
import com.devtime.report.dto.ReportResponses.ClientSummaryCurrencyTotal;
import com.devtime.report.dto.ReportResponses.ClientSummaryReportResponse;
import com.devtime.report.dto.ReportResponses.ReportBalance;
import com.devtime.report.dto.ReportResponses.ReportContract;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportRange;
import com.devtime.report.dto.ReportResponses.ReportTotals;
import com.devtime.shared.time.TenantClock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resumo consolidado por cliente (§7.1 de reports.md).
 *
 * <p>Uma seção por contrato, com o saldo do próprio contrato, e um total consolidado. O saldo de
 * cada seção é a <b>soma dos saldos</b> dos períodos que o intervalo cruza, todos vindos de {@code
 * BalanceService} — nenhuma fórmula de saldo é reimplementada aqui (RP-03). Somar números canônicos
 * é diferente de recalculá-los: se a fórmula mudar, esta soma muda junto.
 *
 * <p>CE-R-09 / CE-C-07: contratos em moedas diferentes produzem totais <b>separados por moeda</b>.
 * Não há conversão, e não há um total único — escolher uma taxa de câmbio seria uma decisão de
 * negócio que documento algum toma, e somar moedas distintas produziria um número sem significado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientSummaryAssembler {

    private final ContractService contractService;
    private final ContractPeriodService periodService;
    private final BalanceService balanceService;
    private final ReportHeaderBuilder headerBuilder;
    private final ReportEntryLoader entryLoader;
    private final ReportGroupingPolicy groupingPolicy;
    private final ReportSummaryBuilder summaryBuilder;
    private final ReportFinancialCalculator financialCalculator;
    private final DurationFormatter durationFormatter;
    private final MoneyFormatter moneyFormatter;
    private final TenantClock clock;

    public ClientSummaryReportResponse assemble(
            UUID clientId, ReportFilters filters, ReportGrouping grouping, UUID restrictToUserId) {
        boolean includeFinancial = entryLoader.includeFinancial(filters);

        List<ReportEntry> entries =
                entryLoader.load(
                        entryLoader.filterFor(
                                null,
                                null,
                                clientId,
                                null,
                                filters.from(),
                                filters.to(),
                                filters,
                                restrictToUserId),
                        // O detalhamento consolidado cruza contratos, e cada um pode ter a sua
                        // taxa. Sem base comum, o valor por linha é omitido; ele aparece dentro de
                        // cada seção, onde o contrato — e portanto a taxa — é único.
                        null);

        List<ClientSummaryContractSection> sections = sections(clientId, filters, includeFinancial);

        log.info(
                "relatório gerado tipo={} fonte=LIVE cliente={} contratos={} linhas={}",
                ReportType.CLIENT_SUMMARY,
                clientId,
                sections.size(),
                entries.size());

        return new ClientSummaryReportResponse(
                ReportType.CLIENT_SUMMARY,
                clock.now(),
                headerBuilder.currentUser(),
                headerBuilder.issueId(null),
                ReportSource.LIVE,
                // O consolidado agrega intervalos que podem conter período aberto; é sempre parcial
                // pela mesma razão da folha de horas (CX-23).
                true,
                grouping,
                headerBuilder.issuer(),
                headerBuilder.client(clientId),
                new ReportRange(filters.from(), filters.to()),
                sections,
                totalsByCurrency(sections),
                groupingPolicy.group(entries, grouping, headerBuilder.locale()),
                summaryBuilder.summaries(entries, entryLoader.includeByUser()),
                summaryBuilder.totals(entries));
    }

    /** Uma seção por contrato do cliente, respeitando o filtro {@code contractIds} do pedido. */
    private List<ClientSummaryContractSection> sections(
            UUID clientId, ReportFilters filters, boolean includeFinancial) {
        List<UUID> contractIds = contractService.findIdsByClient(clientId);
        List<ClientSummaryContractSection> sections = new ArrayList<>(contractIds.size());

        for (UUID contractId : contractIds) {
            if (filters.contractIds() != null
                    && !filters.contractIds().isEmpty()
                    && !filters.contractIds().contains(contractId)) {
                continue;
            }
            sections.add(section(contractId, filters, includeFinancial));
        }
        return List.copyOf(sections);
    }

    private ClientSummaryContractSection section(
            UUID contractId, ReportFilters filters, boolean includeFinancial) {
        ContractReportRef contract = contractService.getReportRef(contractId);
        ReportBalance balance = aggregatedBalance(contractId, filters.from(), filters.to());
        BigDecimal hourlyRate = includeFinancial ? contract.hourlyRate() : null;
        BigDecimal overageRate = includeFinancial ? contract.overageRate() : null;

        return new ClientSummaryContractSection(
                new ReportContract(
                        contract.code(),
                        contract.name(),
                        contract.type(),
                        contract.monthlyMinutes()),
                contract.currency(),
                balance,
                financialCalculator.compose(balance, hourlyRate, overageRate, contract.currency()),
                totalsOf(balance, hourlyRate));
    }

    /**
     * Soma dos saldos dos períodos que o intervalo cruza (BR-149: intervalo fechado nas duas
     * pontas).
     *
     * <p>{@code consumptionRate} é recalculada sobre os agregados em vez de somada: taxas são
     * proporções, e somar proporções de períodos com saldos diferentes produziria um número que não
     * corresponde a nenhum consumo real.
     */
    private ReportBalance aggregatedBalance(UUID contractId, LocalDate from, LocalDate to) {
        int contracted = 0;
        int carriedIn = 0;
        int adjustment = 0;
        int available = 0;
        int consumed = 0;
        int nonBillable = 0;
        int remaining = 0;
        int overage = 0;
        int carriedOut = 0;

        for (ContractPeriodResponse period : periodService.listByContract(contractId)) {
            if (period.endDate().isBefore(from) || period.startDate().isAfter(to)) {
                continue;
            }
            PeriodBalanceResponse balance = balanceService.getBalance(period.id());
            contracted += balance.contractedMinutes();
            carriedIn += balance.carriedInMinutes();
            adjustment += balance.adjustmentMinutes();
            available += balance.availableMinutes();
            consumed += balance.consumedMinutes();
            nonBillable += balance.nonBillableMinutes();
            remaining += balance.remainingMinutes();
            overage += balance.overageMinutes();
        }

        return new ReportBalance(
                contracted,
                carriedIn,
                adjustment,
                available,
                consumed,
                nonBillable,
                remaining,
                overage,
                carriedOut,
                consumptionRate(consumed, available));
    }

    private BigDecimal consumptionRate(int consumed, int available) {
        if (available == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(consumed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP);
    }

    /**
     * Totais da seção derivados do saldo, não das linhas.
     *
     * <p>As contagens de linhas, dias e tickets pertencem ao detalhamento consolidado do relatório,
     * que é único; repeti-las por seção exigiria uma consulta por contrato para produzir números
     * que §7.1 não pede.
     */
    private ReportTotals totalsOf(ReportBalance balance, BigDecimal hourlyRate) {
        int billable = balance.consumedMinutes() - balance.nonBillableMinutes();
        return new ReportTotals(
                0,
                0,
                0,
                balance.consumedMinutes(),
                billable,
                balance.nonBillableMinutes(),
                durationFormatter.toLabel(balance.consumedMinutes()),
                durationFormatter.toDecimalHours(balance.consumedMinutes()),
                moneyFormatter.valueOf(billable, hourlyRate));
    }

    /** CE-R-09: um total por moeda, sem conversão. */
    private List<ClientSummaryCurrencyTotal> totalsByCurrency(
            List<ClientSummaryContractSection> sections) {
        Map<String, int[]> minutesByCurrency = new LinkedHashMap<>();
        Map<String, BigDecimal> valueByCurrency = new LinkedHashMap<>();

        for (ClientSummaryContractSection section : sections) {
            String currency = section.currency();
            minutesByCurrency.computeIfAbsent(currency, ignored -> new int[1])[0] +=
                    section.totals().netMinutes();
            BigDecimal value = section.totals().totalValue();
            if (value != null) {
                valueByCurrency.merge(currency, value, BigDecimal::add);
            }
        }

        List<ClientSummaryCurrencyTotal> totals = new ArrayList<>(minutesByCurrency.size());
        minutesByCurrency.forEach(
                (currency, minutes) ->
                        totals.add(
                                new ClientSummaryCurrencyTotal(
                                        currency,
                                        minutes[0],
                                        durationFormatter.toLabel(minutes[0]),
                                        valueByCurrency.get(currency))));
        return List.copyOf(totals);
    }
}
