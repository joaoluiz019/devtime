package com.devtime.report.dto;

import com.devtime.report.domain.ReportGrouping;
import com.devtime.report.domain.ReportSource;
import com.devtime.report.domain.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Respostas dos cinco relatórios (§6 e §7 de reports.md, §23 de specs/012).
 *
 * <p><b>Estrutura plana, não aninhada em {@code header}.</b> §23 de {@code specs/012} prevê um
 * {@code ReportHeaderDto} agrupando emissor, cliente, contrato e período; §6 de {@code reports.md}
 * mostra os quatro no primeiro nível. Vale {@code reports.md} pela hierarquia IA-11, e o exemplo
 * normativo daquele documento é o contrato que o frontend consome.
 *
 * <p><b>{@code issueId} acrescentado.</b> RN-703 e PDF-07 exigem um identificador único de emissão,
 * rastreável do arquivo até o registro de exportação, e o exemplo de §6 não o traz. A lacuna foi
 * reportada e {@code reports.md} §6 sincronizado.
 *
 * <p>As cinco respostas repetem os campos de identificação em vez de herdá-los de um tipo comum:
 * {@code record} não tem herança, e uma interface selada com {@code default} não reduziria o que
 * cada construtor precisa preencher. A simetria é verificada por teste, não pelo compilador.
 */
public final class ReportResponses {

    private ReportResponses() {}

    /** Quem gerou o relatório. Nunca expõe {@code handle} nem e-mail (§19.1). */
    @Schema(name = "ReportUserRef")
    public record ReportUserRef(UUID id, String name) {}

    @Schema(name = "ReportAddress")
    public record ReportAddress(
            String street,
            String number,
            String complement,
            String district,
            String city,
            String state,
            String postalCode,
            String country) {}

    /**
     * Emissor do documento (RN-703).
     *
     * <p>Em período fechado vem do snapshot e nomeia quem emitiu <b>naquele</b> momento, mesmo que
     * a organização tenha mudado de razão social depois (OB-01).
     */
    @Schema(name = "ReportIssuer")
    public record ReportIssuer(
            String name,
            String legalName,
            String documentNumber,
            String email,
            String phone,
            String logoUrl,
            ReportAddress address) {}

    /** Destinatário do documento (RN-703). Nulo na folha de horas sem recorte por cliente. */
    @Schema(name = "ReportClient")
    public record ReportClient(
            String name, String legalName, String documentNumber, ReportAddress address) {}

    @Schema(name = "ReportContract")
    public record ReportContract(String code, String name, String type, Integer monthlyMinutes) {}

    @Schema(name = "ReportPeriod")
    public record ReportPeriod(
            String label, int sequence, LocalDate startDate, LocalDate endDate, String status) {}

    /** Intervalo livre da folha de horas e do resumo por cliente (§7.1, §7.2). */
    @Schema(name = "ReportRange")
    public record ReportRange(LocalDate from, LocalDate to) {}

    /**
     * Saldo do período, espelhando §6. Todos os campos vêm de {@code BalanceService} ou do payload.
     */
    @Schema(name = "ReportBalance")
    public record ReportBalance(
            int contractedMinutes,
            int carriedInMinutes,
            int adjustmentMinutes,
            int availableMinutes,
            int consumedMinutes,
            int nonBillableMinutes,
            int remainingMinutes,
            int overageMinutes,
            int carriedOutMinutes,
            BigDecimal consumptionRate) {}

    /**
     * Ajuste aplicado ao período, com a justificativa (CP-06 de §6).
     *
     * <p>Listados individualmente por exigência de CP-06: é a justificativa que torna o número
     * defensável perante o cliente. Um total agregado de ajustes diria "faltam 60 minutos" sem
     * dizer por quê.
     */
    @Schema(name = "ReportAdjustment")
    public record ReportAdjustment(
            int minutes,
            String reason,
            String justification,
            String appliedBy,
            Instant appliedAt) {}

    /**
     * Bloco monetário (§6, CP-03).
     *
     * <p>Omitido — o campo inteiro vem nulo — sem {@code CONTRACT_VIEW_FINANCIAL} ou quando o
     * contrato não tem valor hora (CE-R-05, FA-18). A omissão acontece no backend, e vale também
     * para o arquivo exportado (CP-08, SG-07): o arquivo sai do sistema e circula sem controle.
     */
    @Schema(name = "ReportFinancial")
    public record ReportFinancial(
            String currency,
            BigDecimal hourlyRate,
            BigDecimal overageRate,
            int regularMinutes,
            BigDecimal regularValue,
            int overageMinutes,
            BigDecimal overageValue,
            BigDecimal totalValue) {}

    /**
     * Uma linha do detalhamento.
     *
     * @param durationLabel {@code HH:MM} (RN-710, ART-035) — formatado aqui e não na tela, para que
     *     PDF, XLSX, CSV e interface exibam exatamente o mesmo texto
     * @param decimalHours horas decimais com 2 casas; é a coluna 11 do XLSX, a somável (XLS-02)
     * @param value nulo sem {@code CONTRACT_VIEW_FINANCIAL} ou sem valor hora no contrato
     * @param userName {@code Usuário Removido} quando o autor saiu do tenant, em período aberto
     *     (CX-16); em período fechado, o nome congelado no snapshot
     */
    @Schema(name = "ReportEntry")
    public record ReportEntry(
            LocalDate workDate,
            Instant startedAt,
            Instant endedAt,
            String ticketKey,
            String ticketTitle,
            String categoryName,
            String userName,
            String description,
            int netMinutes,
            String durationLabel,
            BigDecimal decimalHours,
            boolean billable,
            List<String> tags,
            BigDecimal value) {}

    /**
     * Um grupo do detalhamento, conforme {@code groupBy}.
     *
     * <p>Com {@code groupBy=NONE} a resposta traz um único grupo de {@code key} nula: é a lista
     * plana de §5.1. Um {@code entries[]} solto ao lado de {@code groups[]} obrigaria todo
     * consumidor — incluindo os três renderers — a tratar dois formatos.
     */
    @Schema(name = "ReportGroup")
    public record ReportGroup(
            String key,
            String label,
            int totalNetMinutes,
            int totalBillableMinutes,
            String durationLabel,
            List<ReportEntry> entries) {}

    /** Uma fatia de {@code summaries} (§6). */
    @Schema(name = "ReportSummarySlice")
    public record ReportSummarySlice(
            String key, String label, String color, int minutes, BigDecimal percentage) {}

    /**
     * Resumos de distribuição (§6).
     *
     * @param byUser omitido — nulo — para {@code MEMBER} (CP-04): ele nunca vê a distribuição do
     *     time
     */
    @Schema(name = "ReportSummaries")
    public record ReportSummaries(
            List<ReportSummarySlice> byCategory,
            List<ReportSummarySlice> byTicket,
            List<ReportSummarySlice> byUser) {}

    @Schema(name = "ReportTotals")
    public record ReportTotals(
            int entriesCount,
            int distinctDays,
            int distinctTickets,
            int netMinutes,
            int billableMinutes,
            int nonBillableMinutes,
            String durationLabel,
            BigDecimal decimalHours,
            BigDecimal totalValue) {}

    /**
     * §6 — o relatório mais importante do produto.
     *
     * @param source {@code SNAPSHOT} ou {@code LIVE} (§6.1); é a única forma de o consumidor saber
     *     se olha um documento definitivo ou um número em evolução
     * @param isPartial RP-02 e CP-02: obriga interface, PDF e XLSX a exibirem a marcação PARCIAL
     * @param reopenCount maior que zero adiciona o aviso de reabertura (FA-03, CA-04)
     */
    @Schema(name = "ContractPeriodReportResponse")
    public record ContractPeriodReportResponse(
            ReportType reportType,
            Instant generatedAt,
            ReportUserRef generatedBy,
            String issueId,
            ReportSource source,
            boolean isPartial,
            int reopenCount,
            Instant snapshotAt,
            ReportGrouping groupBy,
            ReportIssuer issuer,
            ReportClient client,
            ReportContract contract,
            ReportPeriod period,
            ReportBalance balance,
            List<ReportAdjustment> adjustments,
            ReportFinancial financial,
            List<ReportGroup> groups,
            ReportSummaries summaries,
            ReportTotals totals) {}

    /**
     * Uma seção do resumo por cliente: um contrato com o seu próprio saldo (§7.1).
     *
     * @param totals totais do contrato no intervalo, já restritos aos períodos que o intersectam
     */
    @Schema(name = "ClientSummaryContractSection")
    public record ClientSummaryContractSection(
            ReportContract contract,
            String currency,
            ReportBalance balance,
            ReportFinancial financial,
            ReportTotals totals) {}

    /**
     * Total consolidado <b>por moeda</b> (CE-R-09, CE-C-07).
     *
     * <p>Contratos em moedas diferentes produzem totais separados; não há conversão. Somar valores
     * de moedas distintas produziria um número que não significa nada, e escolher uma taxa de
     * câmbio seria decisão de negócio que documento algum toma.
     */
    @Schema(name = "ClientSummaryCurrencyTotal")
    public record ClientSummaryCurrencyTotal(
            String currency, int netMinutes, String durationLabel, BigDecimal totalValue) {}

    /** §7.1. */
    @Schema(name = "ClientSummaryReportResponse")
    public record ClientSummaryReportResponse(
            ReportType reportType,
            Instant generatedAt,
            ReportUserRef generatedBy,
            String issueId,
            ReportSource source,
            boolean isPartial,
            ReportGrouping groupBy,
            ReportIssuer issuer,
            ReportClient client,
            ReportRange range,
            List<ClientSummaryContractSection> contracts,
            List<ClientSummaryCurrencyTotal> totalsByCurrency,
            List<ReportGroup> groups,
            ReportSummaries summaries,
            ReportTotals totals) {}

    /** §7.2 — intervalo livre, independente de contrato. */
    @Schema(name = "TimesheetReportResponse")
    public record TimesheetReportResponse(
            ReportType reportType,
            Instant generatedAt,
            ReportUserRef generatedBy,
            String issueId,
            ReportSource source,
            boolean isPartial,
            ReportGrouping groupBy,
            ReportIssuer issuer,
            ReportRange range,
            List<ReportGroup> groups,
            ReportSummaries summaries,
            ReportTotals totals) {}

    /** Cabeçalho do detalhamento por ticket (§7.3). */
    @Schema(name = "ReportTicket")
    public record ReportTicket(
            String key,
            String title,
            String status,
            String priority,
            Integer estimatedMinutes,
            int spentMinutes) {}

    /** §7.3 — estimativa contra realizado e todos os registros do ticket. */
    @Schema(name = "TicketDetailReportResponse")
    public record TicketDetailReportResponse(
            ReportType reportType,
            Instant generatedAt,
            ReportUserRef generatedBy,
            String issueId,
            ReportSource source,
            boolean isPartial,
            ReportGrouping groupBy,
            ReportIssuer issuer,
            ReportClient client,
            ReportContract contract,
            ReportTicket ticket,
            List<ReportGroup> groups,
            ReportSummaries summaries,
            ReportTotals totals) {}

    /**
     * Linha de produtividade por usuário (§7.4).
     *
     * <p>IDG-02 e CA-13: valores <b>absolutos</b>, sem classificação, sem posição e sem destaque de
     * melhor ou pior. A ordenação é alfabética por nome — qualquer ordenação por métrica seria um
     * ranking com outro nome.
     */
    @Schema(name = "ProductivityByUser")
    public record ProductivityByUser(
            String userName,
            int netMinutes,
            int billableMinutes,
            String durationLabel,
            BigDecimal decimalHours,
            BigDecimal billableRate,
            BigDecimal minutesPerWorkDay) {}

    /** Linha de produtividade por semana ISO (§7.4). */
    @Schema(name = "ProductivityByWeek")
    public record ProductivityByWeek(
            String isoWeek,
            LocalDate weekStart,
            LocalDate weekEnd,
            int netMinutes,
            int billableMinutes,
            String durationLabel) {}

    /** §7.4 — exige {@code REPORT_VIEW_ANY} (CX-21). */
    @Schema(name = "ProductivityReportResponse")
    public record ProductivityReportResponse(
            ReportType reportType,
            Instant generatedAt,
            ReportUserRef generatedBy,
            String issueId,
            ReportSource source,
            boolean isPartial,
            ReportGrouping groupBy,
            ReportIssuer issuer,
            ReportRange range,
            int workDays,
            List<ProductivityByUser> byUser,
            List<ProductivityByWeek> byWeek,
            ReportSummaries summaries,
            ReportTotals totals) {}
}
