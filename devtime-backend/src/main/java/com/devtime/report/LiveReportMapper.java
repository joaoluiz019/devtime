package com.devtime.report;

import com.devtime.contract.dto.BalanceResponses.AdjustmentResponse;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.dto.ContractResponses.ContractReportRef;
import com.devtime.report.ReportDataResolver.FromLive;
import com.devtime.report.dto.ReportResponses.ReportAdjustment;
import com.devtime.report.dto.ReportResponses.ReportBalance;
import com.devtime.report.dto.ReportResponses.ReportContract;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportPeriod;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Entidades atuais → conteúdo do relatório (RN-702, §24).
 *
 * <p>Produz o <b>mesmo</b> {@link ReportBody} que {@link SnapshotReportMapper}. A simetria é o
 * requisito: o frontend, os três renderers e os testes tratam um formato só, e a diferença entre um
 * documento definitivo e um número em evolução fica reduzida a dois campos — {@code source} e
 * {@code isPartial}.
 *
 * <p>Nenhum saldo é recalculado aqui. Todos os números vêm de {@code BalanceService} (RP-03): uma
 * segunda implementação da fórmula canônica divergiria da primeira na próxima mudança de regra, e o
 * relatório é justamente onde a divergência seria descoberta por um cliente, não por um teste.
 */
@Component
@RequiredArgsConstructor
public class LiveReportMapper {

    private final ReportEntryMapper entryMapper;
    private final ReportFinancialCalculator financialCalculator;
    private final ReportHeaderBuilder headerBuilder;

    /**
     * @param workLogs projeções já ordenadas e já restritas ao escopo do solicitante — a ordem é
     *     normativa e vem da consulta (§6.3), nunca daqui
     * @param includeFinancial permissão e opção do pedido, já resolvidas pelo serviço
     */
    public ReportBody map(
            FromLive source,
            ContractReportRef contract,
            List<com.devtime.worklog.dto.WorkLogReportViews.ReportEntry> workLogs,
            List<AdjustmentResponse> adjustments,
            boolean includeFinancial) {
        PeriodBalanceResponse balanceSource = source.balance();
        BigDecimal hourlyRate = includeFinancial ? contract.hourlyRate() : null;
        BigDecimal overageRate = includeFinancial ? contract.overageRate() : null;

        List<ReportEntry> entries = entries(workLogs, hourlyRate);
        ReportBalance balance = balance(balanceSource);

        return new ReportBody(
                headerBuilder.issuer(),
                headerBuilder.client(contract.clientId()),
                contract(contract),
                period(source),
                balance,
                adjustments(adjustments),
                financialCalculator.compose(
                        balance, hourlyRate, overageRate, balanceSource.currency()),
                entries,
                // Ao vivo não existe instante de congelamento: o número muda enquanto se lê.
                null,
                balanceSource.reopenCount(),
                balanceSource.currency());
    }

    /** Detalhamento sem período — folha de horas, detalhe por ticket e produtividade (§7). */
    public List<ReportEntry> entries(
            List<com.devtime.worklog.dto.WorkLogReportViews.ReportEntry> workLogs,
            BigDecimal hourlyRate) {
        return workLogs.stream().map(entry -> entryMapper.fromWorkLog(entry, hourlyRate)).toList();
    }

    private ReportContract contract(ContractReportRef contract) {
        return new ReportContract(
                contract.code(), contract.name(), contract.type(), contract.monthlyMinutes());
    }

    private ReportPeriod period(FromLive source) {
        return new ReportPeriod(
                source.period().label(),
                source.period().sequence(),
                source.period().startDate(),
                source.period().endDate(),
                source.period().status());
    }

    /**
     * {@code carriedOutMinutes} é zero em período aberto, e isso não é uma aproximação: o
     * transporte é calculado no fechamento (passo 2 de RN-241) e não existe antes dele. Exibir uma
     * previsão de transporte num relatório já marcado como parcial acrescentaria um segundo número
     * provisório sem indicação de que é outro tipo de provisório.
     */
    private ReportBalance balance(PeriodBalanceResponse balance) {
        return new ReportBalance(
                balance.contractedMinutes(),
                balance.carriedInMinutes(),
                balance.adjustmentMinutes(),
                balance.availableMinutes(),
                balance.consumedMinutes(),
                balance.nonBillableMinutes(),
                balance.remainingMinutes(),
                balance.overageMinutes(),
                0,
                balance.consumptionRate());
    }

    private List<ReportAdjustment> adjustments(List<AdjustmentResponse> adjustments) {
        if (adjustments == null) {
            return List.of();
        }
        return adjustments.stream()
                .map(
                        adjustment ->
                                new ReportAdjustment(
                                        adjustment.minutes(),
                                        adjustment.reason(),
                                        adjustment.justification(),
                                        // FR-129: o autor é exposto pela API apenas como UUID, e
                                        // exibi-lo é proibido. Mesma resolução do snapshot.
                                        null,
                                        adjustment.appliedAt()))
                .toList();
    }
}
