package com.devtime.ticket.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Exceções de regra da feature 007 (spec §27).
 *
 * <p>BR-063: toda instância nasce de um método fábrica nomeado pela regra que a origina.
 */
public final class TicketExceptions {

    private TicketExceptions() {}

    /** ME-04 / EX-09: a resposta lista as transições possíveis a partir do estado atual. */
    public static BusinessRuleException invalidTransition(
            TicketStatus from, TicketStatus to, Set<TicketStatus> available) {
        return new InvalidTransitionException(from, to, available);
    }

    /** RN-301: contrato ausente ou inválido para o ticket. */
    public static BusinessRuleException contractRequired() {
        return new TicketContractException(
                ErrorCode.TICKET_CONTRACT_REQUIRED, "contractId", "Contrato obrigatório");
    }

    /** RN-306: contrato {@code ENDED} ou {@code CANCELLED} não aceita registro de horas. */
    public static BusinessRuleException contractNotAcceptingWork(String contractStatus) {
        return new TicketContractException(
                ErrorCode.CONTRACT_NOT_ACCEPTING_WORK,
                "contractId",
                "Contrato em " + contractStatus + " não aceita registros");
    }

    /** RN-303: título fora de 3–200 caracteres após aparar. */
    public static BusinessRuleException titleInvalid(int length) {
        return new TicketValidationException(
                ErrorCode.TICKET_TITLE_INVALID,
                Map.of("field", "title", "length", length),
                "O título deve ter entre 3 e 200 caracteres");
    }

    /** RN-304: responsável sem membership {@code ACTIVE} no tenant. */
    public static BusinessRuleException assigneeInvalid() {
        return new TicketValidationException(
                ErrorCode.TICKET_ASSIGNEE_INVALID,
                Map.of("field", "assigneeId"),
                "Responsável inválido ou inativo nesta organização");
    }

    /** RN-104: {@code defaultCategoryId} inexistente, de outro tenant ou inativo. */
    public static BusinessRuleException categoryInvalid() {
        return new TicketValidationException(
                ErrorCode.CATEGORY_INVALID_OR_INACTIVE,
                Map.of("field", "defaultCategoryId"),
                "Categoria inválida ou inativa");
    }

    /** RN-305 / INV-TCK-02: o ticket possui horas apuradas no contrato atual. */
    public static BusinessRuleException contractMoveHasWorkLogs(int spentMinutes) {
        return new TicketConflictException(
                ErrorCode.TICKET_CONTRACT_MOVE_RESTRICTED,
                Map.of("spentMinutes", spentMinutes),
                "Ticket com horas registradas não pode mudar de contrato");
    }

    /** RN-305: mover entre clientes tornaria o histórico de horas incoerente (RN-109). */
    public static BusinessRuleException contractMoveAcrossClients() {
        return new TicketValidationException(
                ErrorCode.TICKET_TARGET_CONTRACT_CLIENT_MISMATCH,
                Map.of("field", "targetContractId"),
                "O contrato de destino pertence a outro cliente");
    }

    /** RN-307 / INV-TCK-03: ticket com horas é cancelável, nunca excluível. */
    public static BusinessRuleException deleteHasWorkLogs(int spentMinutes) {
        return new TicketConflictException(
                ErrorCode.TICKET_DELETE_RESTRICTED,
                Map.of("spentMinutes", spentMinutes, "suggestedAction", "CANCEL"),
                "Ticket com horas não pode ser excluído. Cancele-o");
    }

    /** RN-311: concluir com cronômetro ativo produziria tempo órfão após a conclusão. */
    public static BusinessRuleException activeTimer(List<UUID> timerIds) {
        return new TicketConflictException(
                ErrorCode.TICKET_ACTIVE_TIMER,
                Map.of("activeTimerIds", timerIds),
                "Existe cronômetro ativo neste ticket");
    }

    /** state-machines.md §4.7: {@code blockReason} com no mínimo 5 caracteres. */
    public static BusinessRuleException blockReasonRequired() {
        return new TicketValidationException(
                ErrorCode.TICKET_BLOCK_REASON_REQUIRED,
                Map.of("field", "blockReason", "minLength", 5),
                "Informe o motivo do impedimento (mínimo 5 caracteres)");
    }

    /** ME-04. */
    public static final class InvalidTransitionException extends BusinessRuleException {
        private InvalidTransitionException(
                TicketStatus from, TicketStatus to, Set<TicketStatus> available) {
            super(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    Map.of(
                            "currentStatus", from,
                            "requestedStatus", to,
                            "availableTransitions",
                                    available.stream().map(Enum::name).sorted().toList()),
                    "Não é possível transicionar Ticket de " + from + " para " + to);
        }
    }

    /** RN-301, RN-306. */
    public static final class TicketContractException extends BusinessRuleException {
        private TicketContractException(ErrorCode code, String field, String message) {
            super(code, Map.of("field", field), message);
        }
    }

    /** RN-303, RN-304, RN-104, RN-305 (cliente divergente), §4.7 ({@code blockReason}). */
    public static final class TicketValidationException extends BusinessRuleException {
        private TicketValidationException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }

    /** RN-305, RN-307, RN-311. */
    public static final class TicketConflictException extends BusinessRuleException {
        private TicketConflictException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }
}
