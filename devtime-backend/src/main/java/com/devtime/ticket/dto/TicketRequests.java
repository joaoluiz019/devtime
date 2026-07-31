package com.devtime.ticket.dto;

import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.domain.TicketType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de entrada da feature 007 (tickets.md §5, §8; spec §23).
 *
 * <p>Campos deliberadamente <b>ausentes</b> de todo DTO de escrita, porque campo ausente do
 * contrato é barreira mais forte que campo validado (SG-07, SG-08 da spec):
 *
 * <ul>
 *   <li>{@code number} e {@code key} — imutáveis e derivados do contrato (RN-011, RN-302).
 *   <li>{@code reporterId} — sempre o usuário autenticado.
 *   <li>{@code spentMinutes} e {@code billableMinutes} — desnormalizados, só mudam por work log.
 *   <li>{@code status} — alterado exclusivamente pelo endpoint de transição (ME-05).
 *   <li>{@code contractId} na atualização — tem endpoint próprio, com guardas (RN-305).
 * </ul>
 */
public final class TicketRequests {

    public static final int TITLE_MIN = 3;
    public static final int TITLE_MAX = 200;
    public static final int DESCRIPTION_MAX = 20_000;
    public static final int EXTERNAL_REF_MAX = 200;

    /** RN-313: o limite de formato antecipa o de negócio, sem substituí-lo. */
    public static final int MAX_TAGS = 10;

    private TicketRequests() {}

    /** tickets.md §5. */
    @Schema(name = "TicketCreateRequest")
    public record TicketCreateRequest(
            @NotNull UUID contractId,
            @NotBlank @Size(min = TITLE_MIN, max = TITLE_MAX) String title,
            @Size(max = DESCRIPTION_MAX) String description,
            TicketType type,
            TicketPriority priority,
            UUID assigneeId,
            @Min(0) Integer estimatedMinutes,
            LocalDate dueDate,
            UUID defaultCategoryId,
            @Size(max = MAX_TAGS) List<@NotNull UUID> tagIds,
            @Size(max = MAX_TAGS) List<@NotBlank String> tagNames,
            @Size(max = EXTERNAL_REF_MAX) String externalRef) {}

    /**
     * Atualização dos campos descritivos.
     *
     * <p>{@code version} é obrigatório (RN-004): sem ele, uma edição concorrente sobrescreveria a
     * outra silenciosamente (CE-T-05).
     */
    @Schema(name = "TicketUpdateRequest")
    public record TicketUpdateRequest(
            @NotBlank @Size(min = TITLE_MIN, max = TITLE_MAX) String title,
            @Size(max = DESCRIPTION_MAX) String description,
            @NotNull TicketType type,
            @NotNull TicketPriority priority,
            @Min(0) Integer estimatedMinutes,
            LocalDate dueDate,
            UUID defaultCategoryId,
            @Size(max = MAX_TAGS) List<@NotNull UUID> tagIds,
            @Size(max = EXTERNAL_REF_MAX) String externalRef,
            @NotNull Long version) {}

    /**
     * Transição de situação (tickets.md §8.1).
     *
     * @param blockReason obrigatório apenas quando o destino é {@code BLOCKED}; a exigência é
     *     condicional e por isso mora no serviço, não em anotação
     */
    @Schema(name = "TicketTransitionRequest")
    public record TicketTransitionRequest(
            @NotNull TicketStatus targetStatus,
            @Size(max = 500) String blockReason,
            @NotNull Long version) {}

    /**
     * Atribuição de responsável (tickets.md §8.2).
     *
     * @param assigneeId nulo remove o responsável — permitido e sem notificação (FA-05)
     */
    @Schema(name = "TicketAssignRequest")
    public record TicketAssignRequest(UUID assigneeId, @NotNull Long version) {}

    /**
     * Movimentação entre contratos (tickets.md §8.3).
     *
     * @param confirmed reconhecimento de que a chave legível <b>não</b> muda (RN-011, OB-01). É
     *     registrado na auditoria da movimentação, não usado como guarda: nenhuma regra de {@code
     *     docs/} condiciona a operação a esta confirmação, e transformá-la em bloqueio seria criar
     *     uma regra que não existe (IA-01)
     */
    @Schema(name = "TicketMoveContractRequest")
    public record TicketMoveContractRequest(
            @NotNull UUID targetContractId, boolean confirmed, @NotNull Long version) {}
}
