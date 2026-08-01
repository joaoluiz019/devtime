package com.devtime.comment.event;

import com.devtime.shared.event.DomainEvent;
import java.util.List;
import java.util.UUID;

/**
 * Eventos de domínio de {@code 014-comments} (§15 de {@code specs/013-notifications}).
 *
 * <p>Publicado para que {@code 013-notifications} possa gerar {@code TICKET_COMMENTED} e {@code
 * TICKET_MENTIONED} sem que esta feature conheça aquela — a consumidora é terminal e pode ser
 * cortada sem alterar nada aqui (§22.2 de {@code specs/013}).
 *
 * <p>Está em {@code comment.event}, e não em {@code comment.domain}, pelo mesmo motivo de {@code
 * TicketEvents}: AR-02 proíbe o consumidor de alcançar o pacote de domínio de outra feature.
 */
public final class CommentEvents {

    private CommentEvents() {}

    /**
     * Comentário criado por uma pessoa.
     *
     * <p>Comentários de <b>sistema</b> (RN-815) não geram evento: eles narram um fato que já foi
     * notificado pela feature que o produziu — avisar de novo seria a mesma informação duas vezes.
     *
     * @param authorId NT-05: o consumidor o exclui dos destinatários; ninguém é avisado do próprio
     *     comentário
     * @param assigneeId responsável do ticket no momento do comentário; nulo quando não há
     * @param mentionedUserIds RN-813 — já filtrados para membros ativos por {@code
     *     MentionExtractor}
     */
    public record CommentCreatedEvent(
            UUID commentId,
            UUID ticketId,
            String ticketKey,
            UUID authorId,
            UUID assigneeId,
            List<UUID> mentionedUserIds)
            implements DomainEvent {

        public CommentCreatedEvent {
            mentionedUserIds = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
        }
    }
}
