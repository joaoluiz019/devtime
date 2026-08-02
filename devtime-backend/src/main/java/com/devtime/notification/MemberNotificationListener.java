package com.devtime.notification;

import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import com.devtime.shared.security.Role;
import com.devtime.tenant.MembershipService;
import com.devtime.tenant.event.TenantEvents.MemberJoinedEvent;
import com.devtime.tenant.event.TenantEvents.MemberRemovedEvent;
import com.devtime.user.UserService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notificações de membro (§6 de notifications.md).
 *
 * <p>Fecha a pendência registrada em §14 daquele documento: {@code MEMBER_JOINED} e {@code
 * MEMBER_REMOVED} estavam declarados no catálogo sem produtor, aguardando {@code 002}.
 *
 * <p>Os destinatários seguem o catálogo e diferem entre os dois: o ingresso interessa a quem
 * administra a organização ({@code OWNER} e {@code ADMIN}); a remoção, apenas a {@code OWNER} — é a
 * operação que altera o que aparece nos relatórios, e concentrá-la em quem responde pelo contrato
 * evita transformar movimentação de equipe em ruído.
 *
 * <p>CP-16 / TX-06: {@code AFTER_COMMIT}. Uma falha de notificação não pode reverter a remoção de
 * um membro nem o aceite de um convite.
 */
@Component
@RequiredArgsConstructor
public class MemberNotificationListener {

    private static final String ENTITY_TYPE = "MEMBERSHIP";

    private final NotificationService notificationService;
    private final MembershipService membershipService;
    private final NotificationTemplateRenderer renderer;
    private final DedupeKeyBuilder dedupeKeyBuilder;
    private final UserService userService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberJoined(MemberJoinedEvent event) {
        Set<UUID> recipients =
                membershipService.activeMemberIdsWithRoles(Set.of(Role.OWNER, Role.ADMIN));
        // NT-05: quem entrou não é avisado da própria entrada.
        recipients =
                recipients.stream()
                        .filter(id -> !id.equals(event.userId()))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (recipients.isEmpty()) {
            return;
        }
        String name = userService.summaryOf(event.userId()).name();
        var text = renderer.memberJoined(name);
        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.MEMBER_JOINED,
                        NotificationType.MEMBER_JOINED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(Map.of("membershipId", event.membershipId().toString())),
                        ENTITY_TYPE,
                        event.membershipId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.memberJoined(event.membershipId()))));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberRemoved(MemberRemovedEvent event) {
        Set<UUID> recipients =
                membershipService.activeMemberIdsWithRoles(Set.of(Role.OWNER)).stream()
                        .filter(id -> !id.equals(event.actorId()))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (recipients.isEmpty()) {
            return;
        }
        String name = userService.summaryOf(event.targetUserId()).name();
        var text = renderer.memberRemoved(name, event.preservedWorkLogs());
        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.MEMBER_REMOVED,
                        NotificationType.MEMBER_REMOVED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(
                                Map.of(
                                        "membershipId", event.membershipId().toString(),
                                        "discardedTimers", event.discardedTimers(),
                                        "reassignedTickets", event.reassignedTickets())),
                        ENTITY_TYPE,
                        event.membershipId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.memberRemoved(event.membershipId()))));
    }
}
