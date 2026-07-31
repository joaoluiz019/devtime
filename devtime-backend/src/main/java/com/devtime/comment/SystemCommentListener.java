package com.devtime.comment;

import com.devtime.comment.domain.SystemCommentTrigger;
import com.devtime.ticket.event.TicketEvents.TicketAssignedEvent;
import com.devtime.ticket.event.TicketEvents.TicketContractMovedEvent;
import com.devtime.ticket.event.TicketEvents.TicketStatusChangedEvent;
import com.devtime.user.UserService;
import com.devtime.user.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Geração de comentários de sistema a partir dos fatos de {@code 007-tickets} (RN-815).
 *
 * <p>{@code @EventListener} e <b>não</b> {@code @TransactionalEventListener(AFTER_COMMIT)}: BR-183
 * separa por criticidade, e §15 de ambas as specs classifica o comentário de sistema como parte do
 * mesmo fato da transição — um status alterado sem o comentário correspondente deixa a linha do
 * tempo incompleta. Notificação, que é entrega externa, é que vai depois do commit.
 *
 * <p>Esta classe é o que fecha a dívida OB-06 de {@code specs/007-tickets}: até {@code 014}
 * existir, o emissor daquela feature produzia apenas o {@code AuditLog}. Também é o que mantém as
 * duas features acíclicas — {@code 007} publica e não conhece {@code 014}.
 */
@Component
@RequiredArgsConstructor
public class SystemCommentListener {

    private final SystemCommentService systemCommentService;
    private final SystemCommentTemplates templates;
    private final UserService userService;

    /** RN-815, gatilho 1: mudança de situação, manual ou automática (RN-312). */
    @EventListener
    public void onStatusChanged(TicketStatusChangedEvent event) {
        String body =
                event.blockReason() != null
                        ? templates.blocked(event.blockReason())
                        : event.automatic()
                                ? templates.statusChangedAutomatically(event.from(), event.to())
                                : templates.statusChanged(event.from(), event.to());
        systemCommentService.emit(event.ticketId(), SystemCommentTrigger.STATUS_CHANGED, body);
    }

    /** RN-815, gatilho 2: alteração de responsável. */
    @EventListener
    public void onAssigned(TicketAssignedEvent event) {
        systemCommentService.emit(
                event.ticketId(),
                SystemCommentTrigger.ASSIGNEE_CHANGED,
                templates.assigneeChanged(
                        nameOf(event.previousAssigneeId()), nameOf(event.assigneeId())));
    }

    /** RN-815, gatilho 3: alteração de contrato. */
    @EventListener
    public void onContractMoved(TicketContractMovedEvent event) {
        systemCommentService.emit(
                event.ticketId(),
                SystemCommentTrigger.CONTRACT_MOVED,
                templates.contractMoved(
                        event.previousContractCode(), event.contractCode(), event.ticketKey()));
    }

    /**
     * Nome exibido de uma pessoa.
     *
     * <p>O comentário de sistema guarda o <b>nome</b>, não o identificador: ele é lido por pessoas
     * meses depois, e um UUID na conversa não informa nada. RN-458 garante que um membro removido
     * apareça como "Usuário Removido" em vez de quebrar a leitura.
     */
    private String nameOf(java.util.UUID userId) {
        if (userId == null) {
            return null;
        }
        UserSummary summary = userService.summaryOf(userId);
        return summary == null ? null : summary.name();
    }
}
