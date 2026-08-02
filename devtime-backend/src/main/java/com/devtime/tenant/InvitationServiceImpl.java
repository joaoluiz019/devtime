package com.devtime.tenant;

import com.devtime.audit.AuditService;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.domain.TenantExceptions;
import com.devtime.tenant.dto.MemberRequests.InvitationRequest;
import com.devtime.tenant.dto.MemberResponses.MemberInvitationResponse;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import com.devtime.tenant.event.TenantEvents.MemberInvitedEvent;
import com.devtime.user.UserAccountService;
import com.devtime.user.dto.UserAccount;
import com.devtime.user.dto.UserCommands.NewAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link InvitationService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class InvitationServiceImpl implements InvitationService {

    /** RN-457: validade do convite. */
    static final Duration EXPIRATION = Duration.ofDays(7);

    /** BR-186: teto de convites expirados por execução do job. */
    static final int EXPIRATION_BATCH_SIZE = 200;

    static final String ENTITY_TYPE = "MEMBERSHIP";
    static final String ACTION_INVITED = "MEMBERSHIP_INVITED";
    static final String ACTION_INVITATION_REVOKED = "MEMBERSHIP_INVITATION_REVOKED";
    static final String ACTION_INVITATION_EXPIRED = "MEMBERSHIP_INVITATION_EXPIRED";

    private final MembershipRepository repository;
    private final MemberGuards guards;
    private final InvitationTokenPort invitationTokenPort;
    private final TenantService tenantService;
    private final UserAccountService userAccountService;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantContext tenantContext;
    private final Clock clock;

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'MEMBER_INVITE')")
    public MemberInvitationResponse invite(InvitationRequest request) {
        // Nota ¹: ADMIN não concede OWNER. Sem papel atual a comparar — o convidado ainda não tem
        // vínculo —, a guarda avalia apenas o papel pretendido.
        guards.assertHierarchyAllowed(null, request.role());

        String email = normalize(request.email());
        UUID userId = resolveOrCreateAccount(email);
        assertNotAlreadyLinked(userId);

        Instant now = clock.instant();
        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setRole(request.role());
        membership.setStatus(MembershipStatus.INVITED);
        membership.setInvitedBy(tenantContext.requireUserId()); // BR-041: nunca da requisição
        membership.setInvitedAt(now);
        membership.setRoleChangedAt(now);
        Membership saved = repository.save(membership);

        UUID tenantId = tenantContext.requireTenantId();
        var issued = invitationTokenPort.issue(userId, tenantId);
        auditService.record(
                ACTION_INVITED,
                ENTITY_TYPE,
                saved.getId(),
                Map.of(),
                // ART-084 / CP-12: o e-mail em claro não entra na trilha; o vínculo já identifica
                // a pessoa pelo userId.
                Map.of("role", request.role().name(), "userId", userId.toString()));
        events.publish(
                new MemberInvitedEvent(
                        saved.getId(),
                        tenantId,
                        tenantService.require(tenantId).name(),
                        userId,
                        membership.getInvitedBy(),
                        request.role(),
                        issued.rawToken()));
        return toResponse(saved, email, issued.expiresAt());
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'MEMBER_INVITE')")
    public MemberInvitationResponse resend(UUID membershipId) {
        Membership membership = requireInvited(membershipId);
        Instant now = clock.instant();
        membership.setInvitedAt(now); // Reinicia a janela de 7 dias a partir do novo envio.

        // RN-457: a emissão invalida o token anterior; um link antigo em caixa de entrada deixa de
        // funcionar imediatamente.
        var issued = invitationTokenPort.issue(membership.getUserId(), membership.getTenantId());
        UserAccount account = userAccountService.require(membership.getUserId());
        events.publish(
                new MemberInvitedEvent(
                        membership.getId(),
                        membership.getTenantId(),
                        tenantService.require(membership.getTenantId()).name(),
                        membership.getUserId(),
                        membership.getInvitedBy(),
                        membership.getRole(),
                        issued.rawToken()));
        return toResponse(membership, account.email(), issued.expiresAt());
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'MEMBER_REMOVE')")
    public void revoke(UUID membershipId) {
        Membership membership = requireInvited(membershipId);
        membership.setStatus(MembershipStatus.REMOVED);
        auditService.record(
                ACTION_INVITATION_REVOKED,
                ENTITY_TYPE,
                membership.getId(),
                Map.of("status", MembershipStatus.INVITED.name()),
                Map.of("status", MembershipStatus.REMOVED.name()));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'MEMBER_VIEW')")
    public List<MemberInvitationResponse> listPending() {
        return repository.findByStatus(MembershipStatus.INVITED).stream()
                .map(
                        membership ->
                                toResponse(
                                        membership,
                                        userAccountService.require(membership.getUserId()).email(),
                                        expiresAt(membership)))
                .toList();
    }

    /**
     * RN-457: convites vencidos passam a {@code REMOVED}.
     *
     * <p>Sem {@code @PreAuthorize}: executa como sistema, no job. BR-185: idempotente por predicado
     * — um convite já removido não é selecionado de novo.
     */
    @Override
    @Transactional
    public int expirePending() {
        Instant threshold = clock.instant().minus(EXPIRATION);
        List<Membership> expired =
                repository.findExpiredInvitations(
                        threshold, PageRequest.of(0, EXPIRATION_BATCH_SIZE));
        expired.forEach(
                membership -> {
                    membership.setStatus(MembershipStatus.REMOVED);
                    auditService.recordSystemAction(
                            ACTION_INVITATION_EXPIRED,
                            ENTITY_TYPE,
                            membership.getId(),
                            Map.of("status", MembershipStatus.REMOVED.name()));
                });
        return expired.size();
    }

    /**
     * CE-U-01: um endereço com conta existente é apenas vinculado.
     *
     * <p>A conta criada aqui nasce em {@code PENDING_ACTIVATION} com senha aleatória descartada:
     * quem aceita o convite define a própria senha (§5.12 de authentication.md). A senha aleatória
     * existe para satisfazer a coluna {@code NOT NULL} sem abrir uma janela de acesso — ela não é
     * comunicada a ninguém e nenhum caminho a recupera.
     */
    private UUID resolveOrCreateAccount(String email) {
        return userAccountService
                .findByEmail(email)
                .map(UserAccount::id)
                .orElseGet(
                        () ->
                                userAccountService.create(
                                        new NewAccount(
                                                email,
                                                UUID.randomUUID() + "-" + UUID.randomUUID(),
                                                email,
                                                null)));
    }

    /**
     * CX-06 vs. §7.2: vínculo {@code ACTIVE}, {@code INVITED} ou {@code SUSPENDED} bloqueia; {@code
     * REMOVED} não — a readmissão gera um novo vínculo, preservando o histórico do anterior.
     */
    private void assertNotAlreadyLinked(UUID userId) {
        repository
                .findByUserId(userId)
                .filter(existing -> existing.getStatus() != MembershipStatus.REMOVED)
                .ifPresent(
                        existing -> {
                            throw TenantExceptions.alreadyMember(); // DEVTIME-2459
                        });
    }

    private Membership requireInvited(UUID membershipId) {
        Membership membership =
                repository
                        .findById(membershipId)
                        .orElseThrow(
                                () -> EntityNotFoundException.of(Membership.class, membershipId));
        if (membership.getStatus() != MembershipStatus.INVITED) {
            throw TenantExceptions.alreadyMember();
        }
        return membership;
    }

    /**
     * AU-03: minúsculas e sem espaços nas bordas.
     *
     * <p>Repetida aqui em vez de reusar {@code EmailNormalizer} de {@code auth}: aquela classe é
     * interna à feature 001 e {@code tenant → auth} fecharia um ciclo (BR-008).
     */
    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private Instant expiresAt(Membership membership) {
        return membership.getInvitedAt() == null
                ? null
                : membership.getInvitedAt().plus(EXPIRATION);
    }

    private MemberInvitationResponse toResponse(
            Membership membership, String email, Instant expiresAt) {
        return new MemberInvitationResponse(
                membership.getId(),
                email,
                membership.getRole(),
                MembershipState.valueOf(membership.getStatus().name()),
                membership.getInvitedAt(),
                expiresAt);
    }
}
