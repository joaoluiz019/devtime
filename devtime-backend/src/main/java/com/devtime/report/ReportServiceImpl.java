package com.devtime.report;

import com.devtime.contract.AdjustmentService;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.ContractResponses.ContractReportRef;
import com.devtime.report.ReportDataResolver.FromLive;
import com.devtime.report.ReportDataResolver.FromSnapshot;
import com.devtime.report.ReportDataResolver.PeriodDataSource;
import com.devtime.report.domain.ReportGrouping;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.report.dto.ReportResponses.ClientSummaryReportResponse;
import com.devtime.report.dto.ReportResponses.ContractPeriodReportResponse;
import com.devtime.report.dto.ReportResponses.ProductivityReportResponse;
import com.devtime.report.dto.ReportResponses.ReportContract;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportGroup;
import com.devtime.report.dto.ReportResponses.ReportRange;
import com.devtime.report.dto.ReportResponses.ReportTicket;
import com.devtime.report.dto.ReportResponses.TicketDetailReportResponse;
import com.devtime.report.dto.ReportResponses.TimesheetReportResponse;
import com.devtime.shared.time.TenantClock;
import com.devtime.ticket.TicketService;
import com.devtime.ticket.dto.TicketResponses.TicketReportRef;
import com.devtime.worklog.dto.WorkLogReportViews.ReportEntryFilter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os cinco relatórios, aplicando a ordem normativa da §6.2 (spec 012 §22.2).
 *
 * <p><b>A ordem é o contrato</b> (BR-062). Permissão, escopo, existência, intervalo, fonte,
 * filtros, exclusão lógica, cabeçalho, formatação. O passo mais consequente é o segundo preceder o
 * terceiro: um {@code MEMBER} que pede o relatório de um contrato ao qual não tem acesso recebe
 * {@code 403} por escopo <b>antes</b> de o sistema confirmar que o contrato existe. Inverter os
 * dois vazaria, pelo código de erro, a existência de contratos alheios (OB-04).
 *
 * <p>§28 e CP-18: <b>nenhum log carrega conteúdo de linha</b>. Descrição de work log é texto livre
 * com dado pessoal de terceiros; o que sai no log é tipo, fonte, contagem e duração.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final ReportDataResolver dataResolver;
    private final ReportScopePolicy scopePolicy;
    private final ReportPeriodValidator periodValidator;
    private final ReportGroupingPolicy groupingPolicy;
    private final ReportHeaderBuilder headerBuilder;
    private final ReportEntryLoader entryLoader;
    private final ReportSummaryBuilder summaryBuilder;
    private final SnapshotReportMapper snapshotMapper;
    private final LiveReportMapper liveMapper;
    private final ClientSummaryAssembler clientSummaryAssembler;
    private final ProductivityAssembler productivityAssembler;
    private final ContractService contractService;
    private final AdjustmentService adjustmentService;
    private final TicketService ticketService;
    private final TenantClock clock;

    @Override
    @PreAuthorize(
            "hasPermission(null, 'REPORT_VIEW_ANY') or hasPermission(null, 'REPORT_VIEW_OWN')")
    public ContractPeriodReportResponse contractPeriod(UUID periodId, ReportFilters filters) {
        // Passo 2 de §6.2 — antes do passo 3, e é essa ordem que impede o vazamento por erro.
        Optional<UUID> scope = scopePolicy.resolve(ReportType.CONTRACT_PERIOD, filters);
        ReportGrouping grouping =
                groupingPolicy.validate(ReportType.CONTRACT_PERIOD, filters.groupBy());

        // Passos 3 e 5: existência do período e resolução da fonte (§6.1).
        PeriodDataSource source = dataResolver.resolve(periodId);
        boolean includeFinancial = entryLoader.includeFinancial(filters);
        ReportBody body = bodyOf(source, filters, scope.orElse(null), includeFinancial);

        Locale locale = headerBuilder.locale();
        List<ReportGroup> groups = groupingPolicy.group(body.entries(), grouping, locale);

        log.info(
                "relatório gerado tipo={} fonte={} periodo={} linhas={}",
                ReportType.CONTRACT_PERIOD,
                source.source(),
                periodId,
                body.entries().size());

        return new ContractPeriodReportResponse(
                ReportType.CONTRACT_PERIOD,
                clock.now(),
                headerBuilder.currentUser(),
                headerBuilder.issueId(null),
                source.source(),
                source.isPartial(),
                body.reopenCount(),
                body.snapshotAt(),
                grouping,
                body.issuer(),
                body.client(),
                body.contract(),
                body.period(),
                body.balance(),
                body.adjustments(),
                body.financial(),
                groups,
                summaryBuilder.summaries(body.entries(), entryLoader.includeByUser()),
                summaryBuilder.totals(body.entries()));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW_ANY')")
    public ClientSummaryReportResponse clientSummary(UUID clientId, ReportFilters filters) {
        Optional<UUID> scope = scopePolicy.resolve(ReportType.CLIENT_SUMMARY, filters);
        ReportGrouping grouping =
                groupingPolicy.validate(ReportType.CLIENT_SUMMARY, filters.groupBy());
        periodValidator.assertValidRange(filters.from(), filters.to());

        return clientSummaryAssembler.assemble(clientId, filters, grouping, scope.orElse(null));
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'REPORT_VIEW_ANY') or hasPermission(null, 'REPORT_VIEW_OWN')")
    public TimesheetReportResponse timesheet(ReportFilters filters) {
        Optional<UUID> scope = scopePolicy.resolve(ReportType.TIMESHEET, filters);
        ReportGrouping grouping = groupingPolicy.validate(ReportType.TIMESHEET, filters.groupBy());
        periodValidator.assertValidRange(filters.from(), filters.to());

        boolean includeFinancial = entryLoader.includeFinancial(filters);
        ReportEntryFilter entryFilter =
                entryLoader.filterFor(
                        singleOrNull(filters.contractIds()),
                        null,
                        singleOrNull(filters.clientIds()),
                        null,
                        filters.from(),
                        filters.to(),
                        filters,
                        scope.orElse(null));

        // §7.2 cruza contratos por definição, e contratos podem ter valores hora diferentes. Sem
        // um contrato único não existe taxa a aplicar, então a folha de horas não traz valores —
        // é o mesmo princípio de CE-R-09: na ausência de uma base comum, não se inventa uma.
        BigDecimal hourlyRate = hourlyRateOfSingleContract(filters, includeFinancial);
        List<ReportEntry> entries = entryLoader.load(entryFilter, hourlyRate);

        log.info(
                "relatório gerado tipo={} fonte=LIVE de={} ate={} linhas={}",
                ReportType.TIMESHEET,
                filters.from(),
                filters.to(),
                entries.size());

        return new TimesheetReportResponse(
                ReportType.TIMESHEET,
                clock.now(),
                headerBuilder.currentUser(),
                headerBuilder.issueId(null),
                com.devtime.report.domain.ReportSource.LIVE,
                // CX-23 e RN-702: intervalo livre é sempre parcial. Ele pode conter período aberto,
                // e determinar quais períodos ele cruza para às vezes marcar definitivo produziria
                // um documento cuja natureza mudaria sozinha com o tempo.
                true,
                grouping,
                headerBuilder.issuer(),
                new ReportRange(filters.from(), filters.to()),
                groupingPolicy.group(entries, grouping, headerBuilder.locale()),
                summaryBuilder.summaries(entries, entryLoader.includeByUser()),
                summaryBuilder.totals(entries));
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'REPORT_VIEW_ANY') or hasPermission(null, 'REPORT_VIEW_OWN')")
    public TicketDetailReportResponse ticketDetail(UUID ticketId, ReportFilters filters) {
        Optional<UUID> scope = scopePolicy.resolve(ReportType.TICKET_DETAIL, filters);
        ReportGrouping grouping =
                groupingPolicy.validate(ReportType.TICKET_DETAIL, filters.groupBy());

        TicketReportRef ticket = ticketService.getReportRef(ticketId);
        ContractReportRef contract = contractService.getReportRef(ticket.contractId());
        boolean includeFinancial = entryLoader.includeFinancial(filters);
        BigDecimal hourlyRate = includeFinancial ? contract.hourlyRate() : null;

        List<ReportEntry> entries =
                entryLoader.load(
                        entryLoader.filterFor(
                                null,
                                null,
                                null,
                                ticketId,
                                null,
                                null,
                                filters,
                                scope.orElse(null)),
                        hourlyRate);

        log.info(
                "relatório gerado tipo={} fonte=LIVE ticket={} linhas={}",
                ReportType.TICKET_DETAIL,
                ticketId,
                entries.size());

        return new TicketDetailReportResponse(
                ReportType.TICKET_DETAIL,
                clock.now(),
                headerBuilder.currentUser(),
                headerBuilder.issueId(null),
                com.devtime.report.domain.ReportSource.LIVE,
                true,
                grouping,
                headerBuilder.issuer(),
                headerBuilder.client(contract.clientId()),
                new ReportContract(
                        contract.code(),
                        contract.name(),
                        contract.type(),
                        contract.monthlyMinutes()),
                new ReportTicket(
                        ticket.key(),
                        ticket.title(),
                        ticket.status(),
                        ticket.priority(),
                        ticket.estimatedMinutes(),
                        ticket.spentMinutes()),
                groupingPolicy.group(entries, grouping, headerBuilder.locale()),
                summaryBuilder.summaries(entries, entryLoader.includeByUser()),
                summaryBuilder.totals(entries));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW_ANY')")
    public ProductivityReportResponse productivity(ReportFilters filters) {
        Optional<UUID> scope = scopePolicy.resolve(ReportType.PRODUCTIVITY, filters);
        ReportGrouping grouping =
                groupingPolicy.validate(ReportType.PRODUCTIVITY, filters.groupBy());
        periodValidator.assertValidRange(filters.from(), filters.to());

        return productivityAssembler.assemble(filters, grouping, scope.orElse(null));
    }

    /** §6.1 traduzida em duas chamadas de mapeamento que devolvem o mesmo {@link ReportBody}. */
    private ReportBody bodyOf(
            PeriodDataSource source,
            ReportFilters filters,
            UUID restrictToUserId,
            boolean includeFinancial) {
        if (source instanceof FromSnapshot snapshot) {
            return snapshotMapper.map(
                    snapshot, includeFinancial, restrictToUserId, filters.billableFilter());
        }

        FromLive live = (FromLive) source;
        ContractReportRef contract = contractService.getReportRef(live.balance().contractId());
        ReportEntryFilter entryFilter =
                entryLoader.filterFor(
                        null,
                        live.period().id(),
                        null,
                        null,
                        null,
                        null,
                        filters,
                        restrictToUserId);
        return liveMapper.map(
                live,
                contract,
                entryLoader.rawEntries(entryFilter),
                adjustmentService.listByPeriod(live.period().id()),
                includeFinancial);
    }

    /**
     * Um único contrato no filtro permite aplicar a taxa dele; vários ou nenhum, não.
     *
     * <p>É o mesmo raciocínio de CE-R-09 para moedas: sem base comum, o documento omite o valor em
     * vez de escolher uma base arbitrária.
     */
    private BigDecimal hourlyRateOfSingleContract(ReportFilters filters, boolean includeFinancial) {
        UUID contractId = singleOrNull(filters.contractIds());
        if (!includeFinancial || contractId == null) {
            return null;
        }
        return contractService.getReportRef(contractId).hourlyRate();
    }

    private UUID singleOrNull(List<UUID> ids) {
        return ids != null && ids.size() == 1 ? ids.get(0) : null;
    }
}
