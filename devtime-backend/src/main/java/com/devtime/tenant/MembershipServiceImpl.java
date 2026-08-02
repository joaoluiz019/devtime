package com.devtime.tenant;

import com.devtime.audit.AuditService;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.pagination.PageRequestFactory;
import com.devtime.shared.pagination.PageResponse;
import com.devtime.shared.security.Role;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipExceptions;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.dto.MemberRequests.RoleUpdateRequest;
import com.devtime.tenant.dto.MemberResponses.MemberRemovalResponse;
import com.devtime.tenant.dto.MemberResponses.MemberResponse;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import com.devtime.tenant.dto.TenantViews.MembershipView;
import com.devtime.tenant.event.TenantEvents.MemberRemovedEvent;
import com.devtime.tenant.event.TenantEvents.MemberRoleChangedEvent;
import com.devtime.tenant.event.TenantEvents.MemberSuspendedEvent;
import com.devtime.user.UserAccountService;
import com.devtime.user.dto.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta e ciclo de vida do vínculo (ver {@link MembershipService}).
 *
 * <p>As leituras sem {@code tenantId} explícito são recortadas pelo filtro automático (ART-022). As
 * que recebem {@code tenantId} atendem a fluxos anteriores à seleção de organização e usam
 * consultas {@code @CrossTenant} justificadas no repositório.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MembershipServiceImpl implements MembershipService {

    static final String ENTITY_TYPE = "MEMBERSHIP";
    static final String ACTION_ROLE_CHANGED = "MEMBERSHIP_ROLE_CHANGED";
    static final String ACTION_SUSPENDED = "MEMBERSHIP_SUSPENDED";
    static final String ACTION_REACTIVATED = "MEMBERSHIP_REACTIVATED";
    static final String ACTION_REMOVED = "MEMBERSHIP_REMOVED";

    private final MembershipRepository repository;
    private final MemberMapper mapper;
    private final MemberGuards guards;
    private final MemberRemovalOrchestrator removalOrchestrator;
    private final UserAccountService userAccountService;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantContext tenantContext;
    private final PageRequestFactory pageRequestFactory;
    private final Clock clock;

    @Override
    public boolean isActiveMember(UUID userId) {
        if (userId == null) {
            return false;
        }
        return repository
                .findByUserId(userId)
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
    }

    @Override
    public Set<UUID> activeMemberIds() {
        return repository.findByStatus(MembershipStatus.ACTIVE).stream()
                .map(Membership::getUserId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * RN-607 (ver {@link MembershipService#activeMemberIdsWithRoles}).
     *
     * <p>Sem {@code @PreAuthorize}: é consumido por {@code 013-notifications} a partir de
     * consumidores de evento e de jobs, onde não existe requisição nem permissão a verificar
     * (CE-S-06). Nenhuma rota HTTP o alcança.
     */
    @Override
    public Set<UUID> activeMemberIdsWithRoles(
            java.util.Collection<com.devtime.shared.security.Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.findActiveUserIdsByRoleIn(roles));
    }

    @Override
    @Transactional
    public UUID createOwner(UUID tenantId, UUID userId) {
        Instant now = clock.instant();
        Membership membership = new Membership();
        membership.setTenantId(tenantId);
        membership.setUserId(userId);
        membership.setRole(Role.OWNER);
        membership.setStatus(MembershipStatus.ACTIVE);
        // INV-MEM-04: ACTIVE exige acceptedAt. Quem cria a organização aceita o vínculo no ato.
        membership.setAcceptedAt(now);
        membership.setRoleChangedAt(now); // TK-05
        return repository.save(membership).getId();
    }

    @Override
    public Optional<MembershipView> findByTenantAndUser(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndUserId(tenantId, userId).map(this::toView);
    }

    @Override
    public List<MembershipView> findActiveByUser(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return repository.findActiveByUserId(userId).stream().map(this::toView).toList();
    }

    @Override
    @Transactional
    public int activateInvitedFor(UUID userId) {
        List<Membership> invited = repository.findInvitedByUserId(userId);
        Instant now = clock.instant();
        invited.forEach(
                membership -> {
                    membership.setStatus(MembershipStatus.ACTIVE);
                    membership.setAcceptedAt(now); // INV-MEM-04
                });
        return invited.size();
    }

    @Override
    @Transactional
    public void activate(UUID membershipId, Role role) {
        Membership membership =
                repository
                        .findById(membershipId)
                        .orElseThrow(
                                () -> EntityNotFoundException.of(Membership.class, membershipId));
        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            // Aceite repetido é inofensivo: o vínculo já está no estado desejado (CE-AU-04).
            return;
        }
        if (membership.getStatus() != MembershipStatus.INVITED) {
            // CP-09: readmitir um REMOVED exige novo convite, para preservar o histórico do
            // vínculo anterior. SUSPENDED é reativado por gestão de membros (002), não por link.
            throw MembershipExceptions.notInvited(membership.getStatus().name());
        }
        Instant now = clock.instant();
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setAcceptedAt(now);
        if (role != null && role != membership.getRole()) {
            membership.setRole(role);
            membership.setRoleChangedAt(now); // TK-05
        }
        // §6 de notifications.md: MEMBER_JOINED. O evento nasce aqui, e não em 001, porque é esta
        // feature que decide que o vínculo passou a valer.
        events.publish(
                new com.devtime.tenant.event.TenantEvents.MemberJoinedEvent(
                        membership.getId(), membership.getTenantId(), membership.getUserId()));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'MEMBER_VIEW')")
    public PageResponse<MemberResponse> search(
            Role role, MembershipState status, String search, Pageable pageable) {
        // O termo de busca é resolvido em `users` e devolve identificadores; o recorte por tenant
        // continua sendo o filtro automático sobre memberships (ART-022).
        java.util.Collection<UUID> userIds =
                search == null || search.isBlank()
                        ? null
                        : userAccountService.findIdsMatching(search);
        if (userIds != null && userIds.isEmpty()) {
            return PageResponse.of(Page.empty(pageRequestFactory.validate(pageable)));
        }
        Page<Membership> page =
                repository.search(
                        status == null ? null : MembershipStatus.valueOf(status.name()),
                        role,
                        userIds,
                        pageRequestFactory.validate(pageable));
        Map<UUID, UserAccount> accounts = loadAccounts(page.getContent());
        return PageResponse.of(page, membership -> mapper.toResponse(membership, accounts));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'MEMBER_VIEW')")
    public MemberResponse getById(UUID membershipId) {
        Membership membership = require(membershipId);
        return mapper.toResponse(membership, loadAccounts(List.of(membership)));
    }

    /** Ordem normativa de §6.1: permissão, existência, auto-alteração, hierarquia, último OWNER. */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'MEMBER_UPDATE_ROLE')")
    public MemberResponse changeRole(UUID membershipId, RoleUpdateRequest request) {
        Membership membership = require(membershipId); // 2 — 404 para outro tenant (ART-024)
        assertVersion(membership, request.version()); // RN-004
        guards.assertNotSelf(membership.getUserId()); // 3 — RN-456
        guards.assertHierarchyAllowed(membership.getRole(), request.role()); // 4 — nota ¹
        if (membership.getRole() != request.role()) {
            guards.assertNotLastOwner(membership); // 5 — RN-455, o mais caro por último
        }

        Role previous = membership.getRole();
        if (previous == request.role()) {
            return mapper.toResponse(membership, loadAccounts(List.of(membership)));
        }
        membership.setRole(request.role());
        // 6 — IMP-04: invalida os access tokens do alvo neste tenant. É este campo, comparado ao
        // `iat` do token a cada requisição, que faz o rebaixamento valer em segundos e não em 15
        // minutos.
        membership.setRoleChangedAt(clock.instant());

        auditService.record(
                ACTION_ROLE_CHANGED,
                ENTITY_TYPE,
                membership.getId(),
                Map.of("role", previous.name()),
                Map.of("role", request.role().name()));
        events.publish(
                new MemberRoleChangedEvent(
                        membership.getId(),
                        membership.getTenantId(),
                        membership.getUserId(),
                        previous,
                        request.role(),
                        tenantContext.requireUserId()));
        log.warn(
                "papel alterado targetUserId={} de={} para={}",
                membership.getUserId(),
                previous,
                request.role());
        return mapper.toResponse(membership, loadAccounts(List.of(membership)));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'MEMBER_SUSPEND')")
    public MemberResponse suspend(UUID membershipId) {
        Membership membership = require(membershipId);
        guards.assertNotSelf(membership.getUserId());
        guards.assertHierarchyAllowed(membership.getRole(), null);
        guards.assertNotLastOwner(
                membership); // RN-455: suspender o último OWNER equivale a removê-lo

        MembershipStatus previous = membership.getStatus();
        membership.setStatus(MembershipStatus.SUSPENDED);
        // RN-460: o cronômetro do suspenso é descartado dentro da transação — um membro sem acesso
        // não pode continuar acumulando tempo.
        removalOrchestrator.discardTimersOf(membership.getUserId());

        auditService.record(
                ACTION_SUSPENDED,
                ENTITY_TYPE,
                membership.getId(),
                Map.of("status", previous.name()),
                Map.of("status", MembershipStatus.SUSPENDED.name()));
        events.publish(
                new MemberSuspendedEvent(
                        membership.getId(),
                        membership.getTenantId(),
                        membership.getUserId(),
                        tenantContext.requireUserId()));
        log.warn("membro suspenso targetUserId={}", membership.getUserId());
        return mapper.toResponse(membership, loadAccounts(List.of(membership)));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'MEMBER_SUSPEND')")
    public MemberResponse reactivate(UUID membershipId) {
        Membership membership = require(membershipId);
        if (membership.getStatus() != MembershipStatus.SUSPENDED) {
            // §11.1: REMOVED é terminal; readmitir exige novo convite.
            throw MembershipExceptions.notInvited(membership.getStatus().name());
        }
        membership.setStatus(MembershipStatus.ACTIVE);
        auditService.record(
                ACTION_REACTIVATED,
                ENTITY_TYPE,
                membership.getId(),
                Map.of("status", MembershipStatus.SUSPENDED.name()),
                Map.of("status", MembershipStatus.ACTIVE.name()));
        return mapper.toResponse(membership, loadAccounts(List.of(membership)));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'MEMBER_REMOVE')")
    public MemberRemovalResponse remove(UUID membershipId, UUID reassignTicketsTo) {
        Membership membership = require(membershipId);
        guards.assertHierarchyAllowed(membership.getRole(), null);
        // CX-01: o último OWNER não se remove nem é removido — inclusive quando é ele mesmo quem
        // pede. A auto-remoção é permitida; deixar o tenant sem proprietário, não.
        guards.assertNotLastOwner(membership);

        UUID actorId = tenantContext.requireUserId();
        UUID reassignTo = reassignTicketsTo == null ? actorId : reassignTicketsTo;
        MembershipStatus previous = membership.getStatus();
        Role previousRole = membership.getRole();
        membership.setStatus(MembershipStatus.REMOVED);

        var outcome = removalOrchestrator.apply(membership.getUserId(), reassignTo);

        auditService.record(
                ACTION_REMOVED,
                ENTITY_TYPE,
                membership.getId(),
                Map.of("status", previous.name(), "role", previousRole.name()),
                Map.of(
                        "status", MembershipStatus.REMOVED.name(),
                        "preservedWorkLogs", outcome.preservedWorkLogs(),
                        "reassignedTickets", outcome.reassignedTickets()));
        events.publish(
                new MemberRemovedEvent(
                        membership.getId(),
                        membership.getTenantId(),
                        membership.getUserId(),
                        actorId,
                        outcome.discardedTimers(),
                        outcome.reassignedTickets(),
                        outcome.preservedWorkLogs()));
        return new MemberRemovalResponse(
                MembershipState.REMOVED,
                outcome.preservedWorkLogs(),
                outcome.reassignedTickets(),
                reassignTo,
                outcome.discardedTimers() > 0);
    }

    private Membership require(UUID membershipId) {
        return repository
                .findById(membershipId)
                .orElseThrow(() -> EntityNotFoundException.of(Membership.class, membershipId));
    }

    private void assertVersion(Membership membership, Long expected) {
        long current = membership.getVersion() == null ? 0L : membership.getVersion();
        if (expected == null || expected != current) {
            throw com.devtime.shared.error.BusinessRuleException.versionConflict(
                    ENTITY_TYPE, current);
        }
    }

    private Map<UUID, UserAccount> loadAccounts(List<Membership> memberships) {
        return userAccountService.findAllByIds(
                memberships.stream().map(Membership::getUserId).toList());
    }

    private MembershipView toView(Membership membership) {
        return new MembershipView(
                membership.getId(),
                membership.getTenantId(),
                membership.getUserId(),
                membership.getRole(),
                MembershipState.valueOf(membership.getStatus().name()),
                membership.getRoleChangedAt(),
                membership.getAcceptedAt(),
                membership.getInvitedBy());
    }
}
