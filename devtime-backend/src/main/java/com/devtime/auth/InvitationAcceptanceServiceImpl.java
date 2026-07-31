package com.devtime.auth;

import com.devtime.auth.domain.AuthExceptions;
import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.auth.dto.AuthRequests.AcceptInvitationRequest;
import com.devtime.auth.dto.AuthResponses.InvitationResponse;
import com.devtime.tenant.MembershipService;
import com.devtime.tenant.TenantService;
import com.devtime.tenant.dto.TenantViews.MembershipView;
import com.devtime.tenant.dto.TenantViews.TenantView;
import com.devtime.user.UserAccountService;
import com.devtime.user.UserService;
import com.devtime.user.dto.UserAccount;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link InvitationAcceptanceService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationAcceptanceServiceImpl implements InvitationAcceptanceService {

    private final VerificationTokenService verificationTokenService;
    private final MembershipService membershipService;
    private final TenantService tenantService;
    private final UserAccountService userAccountService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final AuthSessionAssembler sessionAssembler;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuthAuditRecorder auditRecorder;

    @Override
    public InvitationResponse peek(String rawToken) {
        var token = verificationTokenService.peek(rawToken, VerificationTokenType.INVITATION);
        MembershipView membership = requireMembership(token.tenantId(), token.userId());
        TenantView tenant = tenantService.require(token.tenantId());
        UserAccount account = userAccountService.require(token.userId());

        return new InvitationResponse(
                tenant.name(),
                tenant.logoUrl(),
                invitedByName(membership),
                membership.role(),
                account.email(),
                // "Existe" no sentido que a tela precisa: a conta já pode autenticar. Um convite
                // cria a conta em PENDING_ACTIVATION antes de a pessoa definir senha, então a
                // existência da linha em `users` não responde a pergunta que a UI faz — "peço
                // login ou peço cadastro?".
                account.isEmailVerified(),
                token.expiresAt());
    }

    @Override
    @Transactional
    public AuthService.SessionOutcome accept(
            String rawToken,
            AcceptInvitationRequest request,
            UUID authenticatedUserId,
            AuthService.RequestMetadata metadata) {
        var token = verificationTokenService.peek(rawToken, VerificationTokenType.INVITATION);
        MembershipView membership = requireMembership(token.tenantId(), token.userId());
        if (membership.isActive()) {
            throw AuthExceptions.alreadyMember(); // DEVTIME-2459
        }
        if (authenticatedUserId != null && !authenticatedUserId.equals(token.userId())) {
            // Convite endereçado a outra pessoa. Responde "inválido", e não "não é para você": a
            // segunda resposta confirmaria a existência do convite a quem o interceptou.
            throw AuthExceptions.invitationInvalid();
        }

        UserAccount account = userAccountService.require(token.userId());
        if (!account.isEmailVerified()) {
            completeSignup(account, request);
        } else if (authenticatedUserId == null) {
            requirePassword(request);
            if (!userAccountService.matchesPassword(account.id(), request.password())) {
                throw AuthExceptions.invalidCredentials();
            }
        }

        verificationTokenService.consume(rawToken, VerificationTokenType.INVITATION); // RN-457
        membershipService.activate(membership.id(), null);
        auditRecorder.record(
                "MEMBERSHIP_ACTIVATED",
                AuthAuditRecorder.ENTITY_MEMBERSHIP,
                membership.id(),
                Map.of("status", membership.status().name()),
                Map.of("status", "ACTIVE", "role", membership.role().name()));

        if (authenticatedUserId != null) {
            // CX-09 / AC-001-30: a sessão corrente permanece na organização em que já estava. A
            // nova apenas passa a aparecer no seletor.
            return null;
        }
        UserAccount refreshed = userAccountService.require(account.id());
        TenantView tenant = tenantService.require(token.tenantId());
        var issued =
                refreshTokenService.issue(
                        refreshed.id(), tenant.id(), metadata.userAgent(), metadata.ipAddress());
        MembershipView activated =
                membershipService
                        .findByTenantAndUser(tenant.id(), refreshed.id())
                        .orElseThrow(AuthExceptions::membershipInactive);
        return new AuthService.SessionOutcome(
                sessionAssembler.withTenant(refreshed, tenant, activated), issued.rawToken());
    }

    /** Convite para endereço sem conta utilizável: nome e senha são obrigatórios (§5.12). */
    private void completeSignup(UserAccount account, AcceptInvitationRequest request) {
        requirePassword(request);
        passwordPolicyValidator.validate(request.password()); // RN-451
        userAccountService.completeProfile(account.id(), request.fullName());
        userAccountService.changePassword(account.id(), request.password());
        // O aceite prova a posse do endereço tanto quanto o link de verificação: exigir um segundo
        // e-mail de confirmação depois disso seria redundante e travaria o convidado.
        userAccountService.markEmailVerified(account.id());
    }

    private void requirePassword(AcceptInvitationRequest request) {
        if (request == null || request.password() == null || request.password().isBlank()) {
            throw AuthExceptions.invitationPasswordRequired();
        }
    }

    private MembershipView requireMembership(UUID tenantId, UUID userId) {
        return membershipService
                .findByTenantAndUser(tenantId, userId)
                .orElseThrow(AuthExceptions::invitationInvalid);
    }

    private String invitedByName(MembershipView membership) {
        return membership.invitedBy() == null
                ? null
                : userService.summaryOf(membership.invitedBy()).name();
    }
}
