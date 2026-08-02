package com.devtime.tenant.event;

import com.devtime.shared.event.DomainEvent;
import com.devtime.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Eventos de organização e vínculo (spec 002 §15).
 *
 * <p>Em {@code tenant.event}, e não em {@code tenant.domain}, pelo mesmo motivo de {@code
 * TicketEvents} e {@code CommentEvents}: AR-02 proíbe o consumidor de alcançar o pacote de domínio
 * de outra feature.
 *
 * <p>BR-181: os eventos carregam identificadores, nunca entidades.
 */
public final class TenantEvents {

    private TenantEvents() {}

    /** Configuração alterada; consumido por cache e métricas. */
    public record TenantSettingsUpdatedEvent(UUID tenantId, List<String> changedKeys)
            implements DomainEvent {}

    /**
     * Organização cancelada (§4.1 de state-machines.md).
     *
     * <p>Consumido por {@code 001-authentication} para revogar as sessões. A revogação vive lá
     * porque {@code auth} já depende de {@code tenant}; o caminho inverso fecharia um ciclo.
     *
     * @param purgeScheduledAt RN-008: instante em que a purga ocorrerá
     */
    public record TenantCancelledEvent(UUID tenantId, UUID requestedBy, Instant purgeScheduledAt)
            implements DomainEvent {}

    /**
     * Convite emitido; o e-mail é enviado após o commit (BR-128).
     *
     * @param tenantName carregado no evento porque o envio ocorre <b>depois</b> do commit, quando
     *     já não existe contexto de tenant para resolvê-lo — e o convidado precisa ler o nome da
     *     organização, não um identificador
     * @param rawToken valor bruto do convite; existe apenas no caminho do e-mail e nunca é
     *     persistido em claro
     */
    public record MemberInvitedEvent(
            UUID membershipId,
            UUID tenantId,
            String tenantName,
            UUID invitedUserId,
            UUID invitedBy,
            Role role,
            String rawToken)
            implements DomainEvent {}

    /**
     * Papel alterado (IMP-04).
     *
     * <p>A invalidação dos access tokens do alvo <b>não</b> depende deste evento: ela é
     * consequência de {@code membership.roleChangedAt}, comparado a cada requisição pelo {@code
     * SessionValidationService}. O evento serve à notificação, que é efeito colateral.
     */
    public record MemberRoleChangedEvent(
            UUID membershipId,
            UUID tenantId,
            UUID targetUserId,
            Role fromRole,
            Role toRole,
            UUID actorId)
            implements DomainEvent {}

    /** Membro suspenso; as sessões daquele tenant são revogadas. */
    public record MemberSuspendedEvent(
            UUID membershipId, UUID tenantId, UUID targetUserId, UUID actorId)
            implements DomainEvent {}

    /** Convite aceito (§4.3 de state-machines.md); notifica {@code OWNER} e {@code ADMIN}. */
    public record MemberJoinedEvent(UUID membershipId, UUID tenantId, UUID userId)
            implements DomainEvent {}

    /**
     * Membro removido; as sessões daquele tenant são revogadas e os OWNERs, notificados.
     *
     * @param preservedWorkLogs RN-458: viaja no evento para que a notificação possa afirmar o que
     *     foi preservado sem recontar depois do commit, quando o contexto de tenant já não existe
     */
    public record MemberRemovedEvent(
            UUID membershipId,
            UUID tenantId,
            UUID targetUserId,
            UUID actorId,
            int discardedTimers,
            int reassignedTickets,
            long preservedWorkLogs)
            implements DomainEvent {}
}
