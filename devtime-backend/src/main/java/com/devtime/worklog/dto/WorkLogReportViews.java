package com.devtime.worklog.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Projeções de registro de horas na forma que {@code 012-reports} consome (RN-704).
 *
 * <p>Declaradas aqui, e não em {@code report}, porque acompanham {@link
 * com.devtime.worklog.WorkLogService#findForReport}: quem possui a tabela define o que devolve
 * (AR-02). É a direção oposta à de {@link
 * com.devtime.contract.dto.BalanceResponses.PeriodWorkLogEntry}, e a diferença tem motivo — lá é
 * {@code contract} quem declara, porque a dependência é invertida para evitar o ciclo {@code
 * worklog → contract → worklog}; aqui não há ciclo, {@code report} é folha no grafo e simplesmente
 * consome.
 *
 * <p>Distintas de {@code PeriodWorkLogEntry} também no conteúdo: o relatório precisa de {@code
 * categoryId} e {@code userId} para agrupar (§6.3 de specs/012), e o snapshot não — ele congela
 * rótulos, não chaves de agrupamento.
 */
public final class WorkLogReportViews {

    private WorkLogReportViews() {}

    /**
     * Recorte de um relatório.
     *
     * <p>Um objeto e não onze parâmetros (BR-012). Todo campo é opcional exceto o recorte temporal,
     * que os cinco tipos de relatório sempre têm — mesmo o detalhamento por ticket, que o herda do
     * período do contrato.
     *
     * @param restrictToUserId escopo de dados já <b>resolvido</b> por {@code ReportScopePolicy}
     *     (CE-P-10, RN-711). Chega como identificador e não como papel porque a decisão de escopo é
     *     de {@code 012} e acontece antes desta chamada; repeti-la aqui criaria dois lugares onde a
     *     restrição mais dura do sistema poderia divergir
     * @param billable {@code null} inclui faturáveis e não faturáveis; CP-05 de {@code reports.md}
     *     exige que os não faturáveis apareçam no detalhamento, marcados
     */
    public record ReportEntryFilter(
            UUID contractId,
            UUID contractPeriodId,
            UUID clientId,
            UUID ticketId,
            LocalDate from,
            LocalDate to,
            Collection<UUID> userIds,
            Collection<UUID> categoryIds,
            Collection<UUID> tagIds,
            Boolean billable,
            UUID restrictToUserId) {}

    /**
     * Uma linha do detalhamento (§6 de {@code reports.md}, {@code entries[]}).
     *
     * @param userName {@code Usuário Removido} quando o autor não pertence mais ao tenant (CX-16).
     *     Em período fechado o nome vem do snapshot e nunca é substituído — o documento entregue
     *     nomeia quem trabalhou
     * @param tags nomes, não identificadores: é o que o XLSX imprime na coluna 13
     */
    public record ReportEntry(
            UUID id,
            LocalDate workDate,
            Instant startedAt,
            Instant endedAt,
            UUID ticketId,
            String ticketKey,
            String ticketTitle,
            UUID categoryId,
            String categoryName,
            UUID userId,
            String userName,
            String description,
            int netMinutes,
            int billableMinutes,
            boolean billable,
            List<String> tags) {}
}
