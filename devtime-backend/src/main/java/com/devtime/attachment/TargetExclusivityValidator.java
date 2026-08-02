package com.devtime.attachment;

import com.devtime.attachment.domain.AttachmentExceptions;
import com.devtime.attachment.domain.AttachmentTarget;
import com.devtime.comment.CommentService;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.ticket.TicketService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Passos 2 e 3 de §6.1 — exatamente um alvo, existente no tenant (INV-ATT-01).
 *
 * <p>O alvo é validado pelas interfaces públicas de {@code 007} e {@code 014}, nunca pelos
 * repositórios delas (AR-02, BR-002/BR-003).
 *
 * <p>ART-024 / BR-047: alvo de outro tenant produz {@code 404}, indistinguível de inexistente
 * (SG-06, SG-15). Um {@code 403} confirmaria a existência do recurso.
 */
@Component
@RequiredArgsConstructor
public class TargetExclusivityValidator {

    private final TicketService ticketService;
    private final CommentService commentService;

    /**
     * Passo 2: exatamente um alvo informado.
     *
     * <p>Na prática o alvo chega pela rota — {@code /tickets/{id}/attachments} ou {@code
     * /comments/{id}/attachments} —, então o caso de "dois alvos" não é alcançável pela API HTTP. A
     * verificação existe assim mesmo porque FA-17 e §12 a documentam como comportamento, e porque o
     * {@code CHECK} de {@code V023} é a terceira camada da mesma invariante: as três juntas
     * garantem que nenhum caminho futuro reintroduza o estado proibido.
     */
    public AttachmentTarget requireSingleTarget(UUID ticketId, UUID commentId) {
        boolean hasTicket = ticketId != null;
        boolean hasComment = commentId != null;
        if (hasTicket == hasComment) {
            throw AttachmentExceptions.targetExclusivityViolated(); // INV-ATT-01, FA-17
        }
        return hasTicket ? AttachmentTarget.ticket(ticketId) : AttachmentTarget.comment(commentId);
    }

    /** Passo 3: o alvo existe no tenant. Caso contrário, {@code 404 DEVTIME-2002}. */
    public void assertTargetExists(AttachmentTarget target) {
        if (target.isTicket()) {
            ticketService.getById(target.id());
            return;
        }
        if (!commentService.existsForComment(target.id())) {
            // AR-02: o tipo entra como nome — importar `comment.domain.Comment` cruzaria a
            // fronteira da feature 014 apenas para compor uma mensagem.
            throw EntityNotFoundException.of("Comment", target.id());
        }
    }
}
