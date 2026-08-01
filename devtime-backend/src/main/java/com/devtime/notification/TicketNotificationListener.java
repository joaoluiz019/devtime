package com.devtime.notification;

import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.ticket.event.TicketEvents.TicketAssignedEvent;
import com.devtime.ticket.event.TicketEvents.TicketReopenedEvent;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notificações de ticket (RN-607, RN-312).
 *
 * <p>RN-607: o destinatário é o <b>responsável</b> — quem precisa agir é quem carrega o trabalho.
 *
 * <p>FA-21 / CX-22: numa reatribuição, apenas o <b>novo</b> responsável é notificado. O anterior
 * não recebe aviso da remoção: ele não precisa fazer nada, e notificá-lo transformaria uma mudança
 * de planejamento em ruído para quem saiu do assunto.
 *
 * <p>NT-05: quem atribuiu não é notificado da própria atribuição — inclusive quem atribui um ticket
 * a si mesmo, que é o caso mais comum de todos.
 */
@Component
@RequiredArgsConstructor
public class TicketNotificationListener {

    private static final String ENTITY_TYPE = "TICKET";

    private final NotificationService notificationService;
    private final RecipientResolver recipientResolver;
    private final NotificationTemplateRenderer renderer;
    private final DedupeKeyBuilder dedupeKeyBuilder;
    private final TenantContext tenantContext;

    /** RN-607 / FA-21. {@code assigneeId} nulo significa remoção do responsável — nada a avisar. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketAssigned(TicketAssignedEvent event) {
        if (event.assigneeId() == null) {
            return;
        }
        Set<UUID> recipients =
                recipientResolver.forTicketEvent(event.assigneeId(), currentUserId());
        if (recipients.isEmpty()) {
            return;
        }
        var text = renderer.ticketAssigned(event.ticketKey());

        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.TICKET_ASSIGNED,
                        NotificationType.TICKET_ASSIGNED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(Map.of("ticketKey", event.ticketKey())),
                        ENTITY_TYPE,
                        event.ticketId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.ticketAssigned(
                                        event.ticketId(), event.assigneeId()))));
    }

    /**
     * RN-312: um ticket concluído voltou a receber horas.
     *
     * <p>O responsável precisa saber porque o ticket saiu de concluído sem que ele fizesse nada — a
     * reabertura é automática, e sem aviso ele descobriria pelo quadro, dias depois.
     *
     * <p>A chave inclui o {@code workLogId}: cada reabertura é um fato novo, ao contrário da
     * atribuição, que é sempre a mesma pessoa no mesmo ticket.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketReopened(TicketReopenedEvent event) {
        Set<UUID> recipients =
                recipientResolver.forTicketEvent(event.assigneeId(), currentUserId());
        if (recipients.isEmpty()) {
            return;
        }
        var text = renderer.ticketReopened(event.ticketKey());

        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.TICKET_REOPENED,
                        NotificationType.TICKET_REOPENED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(Map.of("ticketKey", event.ticketKey())),
                        ENTITY_TYPE,
                        event.ticketId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.ticketReopened(
                                        event.ticketId(), event.workLogId()))));
    }

    /** NT-05: o autor da ação nunca é destinatário dela. */
    private UUID currentUserId() {
        return tenantContext.currentUserId().orElse(null);
    }
}
