package com.devtime.comment;

import com.devtime.comment.domain.Comment;
import com.devtime.ticket.TicketActivitySource;
import com.devtime.ticket.dto.TicketResponses.TicketActivityEvent;
import com.devtime.user.UserService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contribuição desta feature à linha do tempo do ticket (tickets.md §9.1).
 *
 * <p>Implementa o contrato declarado por {@code 007-tickets}, invertendo a dependência: {@code 007}
 * não conhece {@code 014}, o que mantém as duas features acíclicas (BR-008).
 *
 * <p>O evento carrega uma <b>prévia</b> do corpo, não o texto inteiro: a linha do tempo lista
 * dezenas de eventos e o comentário completo é servido pela rota própria. O limite também protege a
 * resposta de um comentário de 10.000 caracteres.
 */
@Component
@RequiredArgsConstructor
public class CommentActivitySource implements TicketActivitySource {

    /** Caracteres de prévia do corpo na linha do tempo. */
    private static final int PREVIEW_LENGTH = 140;

    private final CommentRepository repository;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public List<TicketActivityEvent> activityOf(UUID ticketId) {
        return repository.findByTicket(ticketId).stream().map(this::toEvent).toList();
    }

    private TicketActivityEvent toEvent(Comment comment) {
        return new TicketActivityEvent(
                comment.isSystem() ? "SYSTEM_COMMENT" : "COMMENT",
                comment.getCreatedAt(),
                comment.getAuthorId() == null ? null : userService.summaryOf(comment.getAuthorId()),
                Map.of(
                        "commentId", comment.getId(),
                        "bodyPreview", preview(comment.getBody()),
                        "isSystem", comment.isSystem()));
    }

    private String preview(String body) {
        if (body.length() <= PREVIEW_LENGTH) {
            return body;
        }
        return body.substring(0, PREVIEW_LENGTH) + "…";
    }
}
