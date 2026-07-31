package com.devtime.comment;

import com.devtime.comment.domain.Comment;
import com.devtime.comment.domain.CommentExceptions;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hierarquia de respostas (RN-814, INV-CMT-01, INV-CMT-02).
 *
 * <p>Um único nível. Responder a uma resposta vincula à <b>raiz</b> daquela resposta (FA-02,
 * CX-03).
 *
 * <p>A normalização ocorre na <b>escrita</b>, não na leitura: um {@code parentCommentId} apontando
 * para outra resposta produziria uma árvore de profundidade arbitrária, e a leitura precisaria
 * achatá-la a cada consulta. Resolver na escrita mantém a estrutura plana por construção.
 */
@Component
@RequiredArgsConstructor
public class CommentThreadPolicy {

    private final CommentRepository repository;

    /**
     * Resolve a raiz à qual a resposta deve ser vinculada.
     *
     * @param parentCommentId comentário indicado pelo autor; nulo cria uma raiz
     * @param ticketId ticket do novo comentário, usado para validar INV-CMT-02
     * @return o identificador da raiz, ou {@code null} quando o comentário é raiz
     * @throws com.devtime.shared.error.BusinessRuleException quando o pai não existe ou pertence a
     *     outro ticket
     */
    public UUID resolveRoot(UUID parentCommentId, UUID ticketId) {
        if (parentCommentId == null) {
            return null;
        }
        Comment parent =
                repository
                        .findById(parentCommentId)
                        .orElseThrow(CommentExceptions::invalidParent); // INV-CMT-02
        if (!parent.getTicketId().equals(ticketId)) {
            // CX-04: responder a comentário de outro ticket criaria uma conversa que aparece em
            // dois lugares e pertence a nenhum.
            throw CommentExceptions.invalidParent();
        }
        // RN-814: se o pai já é resposta, a nova resposta vai para a raiz dele.
        return parent.getParentCommentId() == null ? parent.getId() : parent.getParentCommentId();
    }
}
