package com.devtime.report.render;

import com.devtime.report.domain.ReportSource;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportResponses.ClientSummaryReportResponse;
import com.devtime.report.dto.ReportResponses.ContractPeriodReportResponse;
import com.devtime.report.dto.ReportResponses.ProductivityReportResponse;
import com.devtime.report.dto.ReportResponses.ReportAdjustment;
import com.devtime.report.dto.ReportResponses.ReportBalance;
import com.devtime.report.dto.ReportResponses.ReportClient;
import com.devtime.report.dto.ReportResponses.ReportContract;
import com.devtime.report.dto.ReportResponses.ReportFinancial;
import com.devtime.report.dto.ReportResponses.ReportGroup;
import com.devtime.report.dto.ReportResponses.ReportIssuer;
import com.devtime.report.dto.ReportResponses.ReportPeriod;
import com.devtime.report.dto.ReportResponses.ReportRange;
import com.devtime.report.dto.ReportResponses.ReportSummaries;
import com.devtime.report.dto.ReportResponses.ReportTotals;
import com.devtime.report.dto.ReportResponses.TicketDetailReportResponse;
import com.devtime.report.dto.ReportResponses.TimesheetReportResponse;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Um relatório na forma que os três renderers consomem.
 *
 * <p>Os cinco tipos têm respostas diferentes; os renderers não deveriam ter cinco caminhos cada um.
 * Esta projeção normaliza as diferenças — blocos ausentes viram nulo — para que CSV, XLSX e PDF
 * tratem <b>um</b> formato. Sem ela, a marcação PARCIAL de RN-702 precisaria ser lembrada em quinze
 * lugares (três formatos × cinco tipos), e CP-02 é exatamente a regra que não sobrevive a isso.
 *
 * @param isPartial RP-02: obriga a marcação PARCIAL em <b>todas</b> as páginas do arquivo
 * @param reopenCount maior que zero acrescenta o aviso de reabertura (FA-03)
 */
public record RenderableReport(
        ReportType reportType,
        String issueId,
        Instant generatedAt,
        String generatedByName,
        ReportSource source,
        boolean isPartial,
        int reopenCount,
        Instant snapshotAt,
        ReportIssuer issuer,
        ReportClient client,
        ReportContract contract,
        ReportPeriod period,
        ReportRange range,
        ReportBalance balance,
        List<ReportAdjustment> adjustments,
        ReportFinancial financial,
        List<ReportGroup> groups,
        ReportSummaries summaries,
        ReportTotals totals,
        Locale locale) {

    public static RenderableReport of(ContractPeriodReportResponse report, Locale locale) {
        return new RenderableReport(
                report.reportType(),
                report.issueId(),
                report.generatedAt(),
                name(report.generatedBy()),
                report.source(),
                report.isPartial(),
                report.reopenCount(),
                report.snapshotAt(),
                report.issuer(),
                report.client(),
                report.contract(),
                report.period(),
                report.period() == null
                        ? null
                        : new ReportRange(report.period().startDate(), report.period().endDate()),
                report.balance(),
                report.adjustments(),
                report.financial(),
                report.groups(),
                report.summaries(),
                report.totals(),
                locale);
    }

    public static RenderableReport of(ClientSummaryReportResponse report, Locale locale) {
        return new RenderableReport(
                report.reportType(),
                report.issueId(),
                report.generatedAt(),
                name(report.generatedBy()),
                report.source(),
                report.isPartial(),
                0,
                null,
                report.issuer(),
                report.client(),
                // O resumo por cliente cruza contratos; o cabeçalho não nomeia um deles, e o saldo
                // de cada um aparece na sua própria seção (§7.1).
                null,
                null,
                report.range(),
                null,
                List.of(),
                null,
                report.groups(),
                report.summaries(),
                report.totals(),
                locale);
    }

    public static RenderableReport of(TimesheetReportResponse report, Locale locale) {
        return new RenderableReport(
                report.reportType(),
                report.issueId(),
                report.generatedAt(),
                name(report.generatedBy()),
                report.source(),
                report.isPartial(),
                0,
                null,
                report.issuer(),
                null,
                null,
                null,
                report.range(),
                null,
                List.of(),
                null,
                report.groups(),
                report.summaries(),
                report.totals(),
                locale);
    }

    public static RenderableReport of(TicketDetailReportResponse report, Locale locale) {
        return new RenderableReport(
                report.reportType(),
                report.issueId(),
                report.generatedAt(),
                name(report.generatedBy()),
                report.source(),
                report.isPartial(),
                0,
                null,
                report.issuer(),
                report.client(),
                report.contract(),
                null,
                null,
                null,
                List.of(),
                null,
                report.groups(),
                report.summaries(),
                report.totals(),
                locale);
    }

    /**
     * §7.4 não tem detalhamento por linha: ele agrega por usuário e por semana.
     *
     * <p>Os grupos chegam vazios, e é o renderer que decide o que imprimir — no XLSX, as abas de
     * resumo; no PDF, as tabelas de §7.4. Fabricar linhas sintéticas aqui faria o arquivo prometer
     * um detalhamento que o relatório não tem.
     */
    public static RenderableReport of(ProductivityReportResponse report, Locale locale) {
        return new RenderableReport(
                report.reportType(),
                report.issueId(),
                report.generatedAt(),
                name(report.generatedBy()),
                report.source(),
                report.isPartial(),
                0,
                null,
                report.issuer(),
                null,
                null,
                null,
                report.range(),
                null,
                List.of(),
                null,
                List.of(),
                report.summaries(),
                report.totals(),
                locale);
    }

    /** CP-08: o arquivo omite os valores quando o bloco monetário foi omitido. */
    public boolean hasFinancialColumn() {
        return groups.stream()
                .flatMap(group -> group.entries().stream())
                .anyMatch(entry -> entry.value() != null);
    }

    private static String name(com.devtime.report.dto.ReportResponses.ReportUserRef generatedBy) {
        return generatedBy == null ? null : generatedBy.name();
    }
}
