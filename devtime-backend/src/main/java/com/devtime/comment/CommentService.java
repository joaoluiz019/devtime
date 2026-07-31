package com.devtime.comment;

import com.devtime.comment.dto.CommentRequests.CommentCreateRequest;
import com.devtime.comment.dto.CommentRequests.CommentUpdateRequest;
import com.devtime.comment.dto.CommentResponses.CommentResponse;
import com.devtime.comment.dto.CommentResponses.CommentThreadResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * Interface pública da feature 014 (spec §22.2).
 *
 * <p>{@link #existsForComment(UUID)} é o contrato consumido por {@code 015-attachments} para
 * validar o alvo do anexo (INV-ATT-01).
 */
public interface CommentService {

    /** Conversa do ticket: raízes com respostas, paginada por cursor. */
    CommentThreadResponse listByTicket(UUID ticketId, Instant cursor, int size);

    /** §6.1 da spec: a ordem das validações é normativa. */
    CommentResponse create(UUID ticketId, CommentCreateRequest request);

    /** RN-812: apenas o autor, em até 24 horas. Nem {@code ADMIN} edita comentário de terceiro. */
    CommentResponse update(UUID commentId, CommentUpdateRequest request);

    /** RN-812: autor dentro da janela, ou moderação por {@code COMMENT_DELETE_ANY}. */
    void delete(UUID commentId);

    /** INV-ATT-01: valida o alvo do anexo, para {@code 015-attachments}. */
    boolean existsForComment(UUID commentId);
}
