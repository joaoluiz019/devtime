package com.devtime.worklog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de entrada da feature 008 (worklogs.md §6; spec §23).
 *
 * <p><b>Campos deliberadamente ausentes de todos os DTOs de escrita:</b> {@code contractId} e
 * {@code clientId} (RN-109 — copiados do ticket), {@code contractPeriodId} e {@code workDate}
 * (RN-107/RN-108 — derivados), {@code grossMinutes} e {@code netMinutes} (RN-110/RN-111 — sempre
 * calculados, SG-09), {@code source} e {@code timerId} (RN-126 — do sistema, SG-07), {@code
 * lockedAt} e {@code editCount} (do sistema) e {@code tenantId} (ART-021).
 *
 * <p>A ausência é a garantia: um campo que não existe no contrato não é forjado no payload. Aceitar
 * {@code netMinutes} do cliente permitiria inflar a cobrança com uma única requisição.
 */
public final class WorkLogRequests {

    private WorkLogRequests() {}

    /**
     * Criação manual (§6.1 de specs/008).
     *
     * @param userId RN-106 — nulo significa "para mim"; informar outro exige {@code
     *     WORKLOG_CREATE_FOR_OTHER} e membro ativo
     * @param categoryId RN-104 — quando nulo, a cadeia de pré-seleção resolve
     * @param billable quando nulo, herda {@code category.billableByDefault}
     */
    @Schema(name = "WorkLogCreateRequest")
    public record WorkLogCreateRequest(
            @NotNull UUID ticketId,
            @NotNull Instant startedAt,
            @NotNull Instant endedAt,
            @Min(0) Integer pausedMinutes,
            @NotBlank @Size(min = 3, max = 2000) String description,
            UUID categoryId,
            Boolean billable,
            @Size(max = 10) List<UUID> tagIds,
            UUID userId) {}

    /**
     * Edição (FA-09).
     *
     * <p>{@code ticketId} permanece editável — corrigir o ticket de um lançamento é legítimo —, mas
     * {@code contractId} e {@code clientId} <b>não</b> acompanham a mudança: eles foram congelados
     * na criação (RN-109, INV-WKL-06) e um relatório passado não muda porque o lançamento foi
     * reclassificado hoje.
     *
     * @param version RN-004 — obrigatória; divergência responde {@code DEVTIME-2004}
     */
    @Schema(name = "WorkLogUpdateRequest")
    public record WorkLogUpdateRequest(
            @NotNull UUID ticketId,
            @NotNull Instant startedAt,
            @NotNull Instant endedAt,
            @Min(0) Integer pausedMinutes,
            @NotBlank @Size(min = 3, max = 2000) String description,
            @NotNull UUID categoryId,
            @NotNull Boolean billable,
            @Size(max = 10) List<UUID> tagIds,
            @NotNull Long version) {}

    /**
     * Duplicação (FA-14).
     *
     * <p>Exige <b>novo horário</b>: duplicar mantendo o mesmo intervalo seria criar uma
     * sobreposição perfeita, rejeitada por RN-102 (CX-28). Os demais campos — ticket, categoria,
     * descrição, faturável e etiquetas — são copiados do original.
     */
    @Schema(name = "WorkLogDuplicateRequest")
    public record WorkLogDuplicateRequest(@NotNull Instant startedAt, @NotNull Instant endedAt) {}

    /**
     * Validação prévia (FA-01).
     *
     * <p>Mesmos campos da criação. CP-19 / CE-17: <b>nada é persistido</b> — a resposta relata
     * conflitos, cálculo e prévia de saldo para que o usuário corrija antes de salvar.
     */
    @Schema(name = "WorkLogValidateRequest")
    public record WorkLogValidateRequest(
            @NotNull UUID ticketId,
            @NotNull Instant startedAt,
            @NotNull Instant endedAt,
            @Min(0) Integer pausedMinutes,
            String description,
            UUID categoryId,
            Boolean billable,
            UUID userId,
            /** Preenchido em edição, para excluir o próprio registro da comparação (CX-17). */
            UUID excludeWorkLogId) {}
}
