package com.devtime.notification;

import com.devtime.comment.event.CommentEvents.CommentCreatedEvent;
import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notificações de comentário (RN-813, §6 de notifications.md).
 *
 * <p><b>CE-N-07: o tipo mais específico prevalece.</b> Um responsável que também foi mencionado
 * recebe <b>uma única</b> notificação, do tipo {@code TICKET_MENTIONED} — a menção é o fato mais
 * relevante para ele. Duas notificações do mesmo comentário seriam exatamente o ruído que NT-02
 * existe para evitar.
 *
 * <p>CE-N-06 / NT-05: menção a si mesmo não gera nada, e o autor nunca é notificado do próprio
 * comentário.
 */
@Component
@RequiredArgsConstructor
public class CommentNotificationListener {

    private static final String ENTITY_TYPE = "TICKET";

    private final NotificationService notificationService;
    private final RecipientResolver recipientResolver;
    private final NotificationTemplateRenderer renderer;
    private final DedupeKeyBuilder dedupeKeyBuilder;

    /** CP-16 / TX-06: após o commit — uma falha de e-mail não pode reverter um comentário. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        Set<UUID> mentioned =
                recipientResolver.forExplicitRecipients(
                        new LinkedHashSet<>(event.mentionedUserIds()), event.authorId());

        notifyMentioned(event, mentioned);
        notifyAssignee(event, mentioned);
    }

    private void notifyMentioned(CommentCreatedEvent event, Set<UUID> mentioned) {
        if (mentioned.isEmpty()) {
            return;
        }
        var text = renderer.ticketMentioned(event.ticketKey());
        notificationService.notify(
                new NotificationCommand(
                        mentioned,
                        NotificationType.TICKET_MENTIONED,
                        NotificationType.TICKET_MENTIONED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(Map.of("ticketKey", event.ticketKey())),
                        ENTITY_TYPE,
                        event.ticketId(),
                        // §6.1: a chave inclui o destinatário — cada pessoa mencionada é um fato
                        // próprio do mesmo comentário.
                        recipient ->
                                dedupeKeyBuilder.ticketMentioned(event.commentId(), recipient)));
    }

    /**
     * CE-N-07: o responsável já mencionado <b>não</b> recebe também o aviso de comentário.
     *
     * <p>O filtro acontece aqui, e não no {@code dedupeKey}: as duas chaves são diferentes por
     * construção ({@code TICKET_MENTION} e {@code TICKET_COMMENT}), então a deduplicação não
     * impediria as duas notificações — é a regra que impede.
     */
    private void notifyAssignee(CommentCreatedEvent event, Set<UUID> alreadyMentioned) {
        Set<UUID> assignee = recipientResolver.forTicketEvent(event.assigneeId(), event.authorId());
        Set<UUID> recipients =
                assignee.stream()
                        .filter(userId -> !alreadyMentioned.contains(userId))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (recipients.isEmpty()) {
            return;
        }
        var text = renderer.ticketCommented(event.ticketKey());
        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.TICKET_COMMENTED,
                        NotificationType.TICKET_COMMENTED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(Map.of("ticketKey", event.ticketKey())),
                        ENTITY_TYPE,
                        event.ticketId(),
                        recipient ->
                                dedupeKeyBuilder.ticketCommented(event.commentId(), recipient)));
    }
}
