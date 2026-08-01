package com.devtime.tenant;

import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.security.Role;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipExceptions;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import com.devtime.tenant.dto.TenantViews.MembershipView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
public class MembershipServiceImpl implements MembershipService {

    private final MembershipRepository repository;
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
