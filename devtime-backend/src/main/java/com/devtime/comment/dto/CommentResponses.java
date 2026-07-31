package com.devtime.comment.dto;

import com.devtime.comment.domain.SystemCommentTrigger;
import com.devtime.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs de saída da feature 014 (tickets.md §10.1, spec §23). */
public final class CommentResponses {

    private CommentResponses() {}

    /**
     * Comentário com suas respostas.
     *
     * @param author autor; nome exibido como {@code Usuário Removido} quando o membro saiu (RN-458,
     *     CX-11); nulo em comentário de sistema
     * @param canEdit calculado no <b>servidor</b> — o cliente não reimplementa a janela de 24h
     * @param canDelete idem, considerando também {@code COMMENT_DELETE_ANY}
     * @param replies respostas em ordem cronológica crescente; sempre vazio em uma resposta
     */
    @Schema(name = "CommentResponse")
    public record CommentResponse(
            UUID id,
            UUID ticketId,
            String body,
            UserSummary author,
            UUID parentCommentId,
            List<UserSummary> mentionedUsers,
            boolean isSystem,
            SystemCommentTrigger systemTrigger,
            Instant createdAt,
            Instant editedAt,
            boolean canEdit,
            boolean canDelete,
            long version,
            List<CommentResponse> replies) {}

    /**
     * Conversa paginada por cursor.
     *
     * @param cursor instante da raiz mais antiga devolvida; alimenta a próxima página
     */
    @Schema(name = "CommentThreadResponse")
    public record CommentThreadResponse(
            List<CommentResponse> content, Instant cursor, boolean hasMore, long totalComments) {}
}
