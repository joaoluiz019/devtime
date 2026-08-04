package com.devtime.worklog.dto;

import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs de saída da feature 008 (worklogs.md §5 a §8; spec §23). */
public final class WorkLogResponses {

    private WorkLogResponses() {}

    /** Ticket achatado na resposta do registro. */
    @Schema(name = "WorkLogTicketResponse")
    public record WorkLogTicketResponse(UUID id, String key, String title) {}

    /** Categoria achatada na resposta do registro. */
    @Schema(name = "WorkLogCategoryResponse")
    public record WorkLogCategoryResponse(UUID id, String name, String color) {}

    /**
     * Aviso que acompanha um {@code 201} bem-sucedido (RN-232).
     *
     * <p>Distinto de erro por construção: o registro <b>foi</b> criado. Um aviso devolvido como
     * erro faria a interface descartar o resultado de uma operação que deu certo.
     */
    @Schema(name = "WorkLogWarning")
    public record WorkLogWarning(String code, String message, int exceedingMinutes) {}

    /**
     * Detalhe do registro de horas.
     *
     * @param billableMinutes RN-112 — derivado, nunca persistido
     * @param durationLabel {@code netMinutes} em {@code HH:MM}; formatação é do mapper (BR-106)
     * @param lockedAt RN-121 — não nulo torna o registro somente leitura
     * @param editCount RN-123 — contra-métrica de qualidade de captura
     */
    @Schema(name = "WorkLogResponse")
    public record WorkLogResponse(
            UUID id,
            WorkLogTicketResponse ticket,
            UUID contractId,
            UUID clientId,
            UUID contractPeriodId,
            UserSummary user,
            WorkLogCategoryResponse category,
            LocalDate workDate,
            Instant startedAt,
            Instant endedAt,
            int grossMinutes,
            int pausedMinutes,
            int netMinutes,
            int billableMinutes,
            String durationLabel,
            String description,
            boolean billable,
            String source,
            UUID timerId,
            Instant lockedAt,
            int editCount,
            List<TagOptionResponse> tags,
            Instant createdAt,
            Instant updatedAt,
            long version) {}

    /**
     * Item de listagem — projeção, nunca a entidade (BR-107, CP-15).
     *
     * <p>{@code description} está <b>ausente</b> por decisão: são até 2.000 caracteres por linha, e
     * uma página de 100 itens trafegaria centenas de kilobytes que a lista não exibe.
     */
    @Schema(name = "WorkLogSummaryResponse")
    public record WorkLogSummaryResponse(
            UUID id,
            LocalDate workDate,
            Instant startedAt,
            Instant endedAt,
            String ticketKey,
            UUID ticketId,
            String categoryName,
            UUID userId,
            int netMinutes,
            int billableMinutes,
            String durationLabel,
            boolean billable,
            String source,
            Instant lockedAt) {}

    /**
     * Resposta da criação (§6.1 passo 26).
     *
     * <p>Devolve o <b>saldo já atualizado</b> junto com o registro. É o valor que o usuário mais
     * confere no exato momento em que registra horas, e trazê-lo desatualizado destruiria a
     * confiança no número que é o produto (OB-06).
     */
    @Schema(name = "WorkLogCreatedResponse")
    public record WorkLogCreatedResponse(
            WorkLogResponse workLog,
            PeriodBalanceResponse balance,
            List<WorkLogWarning> warnings) {}

    /**
     * Registro conflitante devolvido por {@code /validate} (FA-01).
     *
     * <p>§19.1: <b>sem descrição</b>. O conflito pode ser de outro membro para {@code MANAGER}+, e
     * a descrição é texto livre com possível dado pessoal.
     */
    @Schema(name = "WorkLogConflictResponse")
    public record WorkLogConflictResponse(
            UUID id, LocalDate workDate, Instant startedAt, Instant endedAt, String ticketKey) {}

    /** Cálculo devolvido pela validação prévia, espelhando RN-110 a RN-113. */
    @Schema(name = "WorkLogCalculationResponse")
    public record WorkLogCalculationResponse(
            int grossMinutes,
            int pausedMinutes,
            int netMinutesBeforeRounding,
            int netMinutes,
            int billableMinutes,
            LocalDate workDate,
            String durationLabel) {}

    /**
     * Prévia do saldo antes de salvar (FA-01).
     *
     * <p>Mostrar o "depois" ao lado do "antes" é o que permite ao usuário decidir marcar o registro
     * como não faturável <b>antes</b> de esbarrar em {@code BLOCK}.
     */
    @Schema(name = "BalancePreviewResponse")
    public record BalancePreviewResponse(
            UUID contractPeriodId,
            int availableMinutes,
            int consumedBeforeMinutes,
            int consumedAfterMinutes,
            int remainingAfterMinutes) {}

    /**
     * Resultado da validação prévia (FA-01, CE-17).
     *
     * <p>CP-19: nada é persistido. Diferente da criação, os problemas são <b>relatados em
     * conjunto</b> em vez de interrompidos no primeiro — o objetivo é permitir corrigir tudo de uma
     * vez.
     */
    @Schema(name = "WorkLogValidateResponse")
    public record WorkLogValidateResponse(
            boolean valid,
            List<WorkLogWarning> errors,
            List<WorkLogWarning> warnings,
            List<WorkLogConflictResponse> conflicts,
            WorkLogCalculationResponse calculated,
            BalancePreviewResponse balancePreview) {}

    /** Dia do calendário mensal (§7 de worklogs.md, P22). */
    @Schema(name = "WorkLogCalendarDay")
    public record WorkLogCalendarDay(
            LocalDate date, int totalMinutes, int billableMinutes, int entryCount) {}

    @Schema(name = "WorkLogCalendarResponse")
    public record WorkLogCalendarResponse(
            LocalDate from, LocalDate to, List<WorkLogCalendarDay> days, int totalMinutes) {}

    /** Total por categoria dentro dos filtros aplicados (§8 de worklogs.md). */
    @Schema(name = "WorkLogCategoryTotal")
    public record WorkLogCategoryTotal(
            UUID categoryId, String categoryName, int totalMinutes, int entryCount) {}

    /**
     * Totais de um intervalo, somados no banco.
     *
     * <p>Publicado a {@code 010-dashboard} para {@code quickStats}. Distinto de {@link
     * WorkLogTotalsResponse}: aqui não há quebra por categoria nem contagem de registros, apenas os
     * dois números que o painel exibe — e é a ausência da quebra que permite resolver a consulta no
     * índice coberto, sem materializar registro algum.
     */
    @Schema(name = "WorkLogRangeTotals")
    public record WorkLogRangeTotals(int netMinutes, int billableMinutes) {

        public static WorkLogRangeTotals empty() {
            return new WorkLogRangeTotals(0, 0);
        }
    }

    /**
     * Minutos somados por um agrupamento qualquer (cliente, categoria ou contrato).
     *
     * <p>Publicado a {@code 010-dashboard} para as fatias dos gráficos de distribuição. Devolve o
     * <b>identificador</b> e não o rótulo: o nome e a cor pertencem às features donas de cada
     * entidade ({@code 003} e {@code 005}), e resolvê-los aqui exigiria que {@code worklog}
     * dependesse delas para uma informação que é de apresentação.
     */
    @Schema(name = "WorkLogGroupTotal")
    public record WorkLogGroupTotal(UUID groupId, int netMinutes, int billableMinutes) {}

    @Schema(name = "WorkLogTotalsResponse")
    public record WorkLogTotalsResponse(
            int totalMinutes,
            int billableMinutes,
            int nonBillableMinutes,
            int entryCount,
            List<WorkLogCategoryTotal> byCategory) {}
}
