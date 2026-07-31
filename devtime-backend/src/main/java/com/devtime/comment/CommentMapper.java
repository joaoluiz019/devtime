package com.devtime.comment;

import com.devtime.comment.domain.Comment;
import com.devtime.comment.dto.CommentResponses.CommentResponse;
import com.devtime.user.dto.UserSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão de {@link Comment} para DTO (ADR-014, BR-104).
 *
 * <p>BR-105: nenhum acesso a banco. Autor, mencionados e as permissões calculadas chegam
 * <b>resolvidos</b> pelo serviço, em consultas em lote — resolvê-los aqui produziria N+1 numa
 * conversa de 20 comentários.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CommentMapper {

    /**
     * Monta a resposta.
     *
     * <p>Escrito como {@code default} porque nenhum dos cinco parâmetros derivados vem da entidade:
     * autor e mencionados pertencem a {@code 002-users}, e {@code canEdit}/{@code canDelete} são
     * decisões de {@link CommentEditPolicy} sobre o requisitante corrente.
     */
    default CommentResponse toResponse(
            Comment comment,
            UserSummary author,
            List<UserSummary> mentionedUsers,
            boolean canEdit,
            boolean canDelete,
            List<CommentResponse> replies) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicketId(),
                comment.getBody(),
                author,
                comment.getParentCommentId(),
                mentionedUsers,
                comment.isSystem(),
                comment.getSystemTrigger(),
                comment.getCreatedAt(),
                comment.getEditedAt(),
                canEdit,
                canDelete,
                comment.getVersion() == null ? 0L : comment.getVersion(),
                replies);
    }
}
