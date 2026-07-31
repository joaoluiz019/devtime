package com.devtime.auth;

import com.devtime.auth.domain.AuthExceptions;
import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.auth.dto.AuthRequests.ChangePasswordRequest;
import com.devtime.auth.dto.AuthRequests.LoginRequest;
import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.auth.dto.AuthRequests.SelectTenantRequest;
import com.devtime.auth.dto.AuthResponses.MeResponse;
import com.devtime.auth.dto.AuthResponses.RegisterResponse;
import com.devtime.auth.dto.AuthResponses.TenantOptionResponse;
import com.devtime.auth.event.AuthEvents.PasswordChangedEvent;
import com.devtime.auth.event.AuthEvents.TenantSelectedEvent;
import com.devtime.auth.event.AuthEvents.UserRegisteredEvent;
import com.devtime.auth.event.AuthEvents.VerificationResentEvent;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.tenant.MembershipService;
import com.devtime.tenant.TenantService;
import com.devtime.tenant.dto.TenantViews.MembershipView;
import com.devtime.tenant.dto.TenantViews.TenantState;
import com.devtime.tenant.dto.TenantViews.TenantView;
import com.devtime.user.UserAccountService;
import com.devtime.user.dto.AccountStatus;
import com.devtime.user.dto.UserAccount;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link AuthService}.
 *
 * <p>A ordem das verificações do login (§6.1 do spec) é <b>normativa</b> (BR-062) e está comentada
 * passo a passo em {@link #login}. Alterá-la reabre canais de enumeração que a ordem atual fecha.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserAccountService userAccountService;
    private final TenantService tenantService;
    private final MembershipService membershipService;
    private final TenantProvisioningService tenantProvisioningService;
    private final VerificationTokenService verificationTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final EmailNormalizer emailNormalizer;
    private final AuthSessionAssembler sessionAssembler;
    private final MeResponseAssembler meAssembler;
    private final AuthAuditRecorder auditRecorder;
    private final AuthMetrics metrics;
    private final DomainEventPublisher events;
    private final Clock clock;

    @Override
    public RegisterResponse register(RegisterRequest request, RequestMetadata metadata) {
        String email = emailNormalizer.normalize(request.email()); // RN-452 / AU-03 / CX-01
        passwordPolicyValidator.validate(request.password()); // RN-451

        // Verificação antecipada de unicidade: produz a mensagem correta no caso comum. A corrida
        // (CX-02) é fechada pelo índice uq_users_email, traduzido para o mesmo DEVTIME-2452 por
        // ConstraintViolationMapper — duas portas, uma resposta.
        if (userAccountService.findByEmail(email).isPresent()) {
            metrics.registerAttempt("duplicate_email");
            throw AuthExceptions.emailAlreadyRegistered(); // RN-452
        }

        var provisioned = tenantProvisioningService.provision(request, email);

        // TX-06 / CP-10: o e-mail sai depois do commit. Falha de entrega não desfaz o cadastro
        // (AQ-09, CX-12) — o retorno informa verificationEmailSent para que a UI ofereça reenvio.
        events.publish(
                new UserRegisteredEvent(
                        provisioned.userId(),
                        provisioned.tenantId(),
                        email,
                        request.fullName(),
                        provisioned.rawVerificationToken()));

        metrics.registerAttempt("success");
        log.info(
                "cadastro concluído userId={} tenantId={}",
                provisioned.userId(),
                provisioned.tenantId());
        return new RegisterResponse(
                provisioned.userId(),
                provisioned.tenantId(),
                email,
                AccountStatus.PENDING_ACTIVATION.name(),
                // O envio é assíncrono ao commit: neste ponto ele foi apenas enfileirado. O valor
                // reflete a intenção de envio; o resultado real chega ao usuário pelo próprio
                // e-mail ou pela ação de reenvio.
                true);
    }

    @Override
    @Transactional
    public SessionOutcome login(LoginRequest request, RequestMetadata metadata) {
        String email = emailNormalizer.normalize(request.email());

        // Passo 2 — usuário existe. AU-01/AU-02/SG-03: quando não existe, o BCrypt é executado
        // mesmo assim e a resposta é idêntica à de senha errada.
        Optional<UserAccount> found = userAccountService.findByEmail(email);
        if (found.isEmpty()) {
            userAccountService.burnPasswordComparison();
            metrics.loginAttempt("bad_credentials");
            throw AuthExceptions.invalidCredentials(); // DEVTIME-1001
        }
        UserAccount account = found.get();

        // Passo 3 — bloqueio (RN-453). Precede a senha para que uma conta bloqueada não sirva de
        // oráculo de senha correta.
        account = loginAttemptService.assertNotLocked(account);

        // Passo 4 — senha.
        if (!userAccountService.matchesPassword(account.id(), request.password())) {
            loginAttemptService.registerFailure(account);
            metrics.loginAttempt("bad_credentials");
            // §28: log sem e-mail em claro nem senha tentada.
            log.info("tentativa de login sem sucesso userId={}", account.id());
            throw AuthExceptions.invalidCredentials(); // DEVTIME-1001
        }

        // Passo 5 — e-mail verificado (CP-08). Depois da senha, para que um atacante não descubra
        // quais endereços existem e estão pendentes de ativação.
        assertUsable(account);

        // Passo 6 — ao menos um vínculo ativo (INV-USR-04).
        List<MembershipView> memberships = membershipService.findActiveByUser(account.id());
        if (memberships.isEmpty()) {
            metrics.loginAttempt("no_membership");
            throw AuthExceptions.noActiveMembership(); // DEVTIME-1003
        }

        loginAttemptService.registerSuccess(account);
        metrics.loginAttempt("success");
        return openSession(account, memberships, metadata);
    }

    @Override
    @Transactional
    public SessionOutcome refresh(String rawRefreshToken, RequestMetadata metadata) {
        var rotated =
                refreshTokenService.rotate(
                        rawRefreshToken, metadata.userAgent(), metadata.ipAddress());
        UserAccount account = userAccountService.require(rotated.userId());
        assertUsable(account);

        List<MembershipView> memberships = membershipService.findActiveByUser(account.id());
        if (memberships.isEmpty()) {
            metrics.refreshAttempt("no_membership");
            throw AuthExceptions.noActiveMembership();
        }

        // CE-AU-08 / FA-11: o papel é relido a cada renovação, então uma alteração feita durante a
        // sessão passa a valer no próximo refresh, sem exigir novo login.
        Optional<MembershipView> current =
                memberships.stream()
                        .filter(m -> m.tenantId().equals(rotated.tenantId()))
                        .findFirst();
        metrics.refreshAttempt("success");
        if (rotated.tenantId() == null || current.isEmpty()) {
            // Sessão sem organização selecionada, ou cujo vínculo deixou de estar ativo: volta ao
            // estado de pré-seleção em vez de encerrar — o usuário pode ter outras organizações.
            return new SessionOutcome(
                    sessionAssembler.pendingTenantSelection(
                            account, tenantService.optionsFor(account.id())),
                    rotated.issued().rawToken());
        }
        TenantView tenant = tenantService.require(rotated.tenantId());
        assertTenantUsable(tenant);
        return new SessionOutcome(
                sessionAssembler.withTenant(account, tenant, current.get()),
                rotated.issued().rawToken());
    }

    @Override
    @Transactional
    public SessionOutcome verifyEmail(String rawToken, RequestMetadata metadata) {
        UUID userId;
        // CE-AU-04 / CA-08: idempotente. Clientes de e-mail com pré-visualização consomem o link
        // antes do usuário; responder erro na segunda chamada quebraria um fluxo legítimo.
        Optional<VerificationTokenService.ConsumedToken> alreadyConsumed =
                verificationTokenService.findConsumed(
                        rawToken, VerificationTokenType.EMAIL_VERIFICATION);
        if (alreadyConsumed.isPresent()) {
            userId = alreadyConsumed.get().userId();
        } else {
            var consumed =
                    verificationTokenService.consume(
                            rawToken, VerificationTokenType.EMAIL_VERIFICATION);
            userId = consumed.userId();
            userAccountService.markEmailVerified(userId);
            // §4.2 de state-machines.md: a verificação ativa os convites pendentes do usuário.
            int activated = membershipService.activateInvitedFor(userId);
            auditRecorder.record(
                    "USER_EMAIL_VERIFIED",
                    AuthAuditRecorder.ENTITY_USER,
                    userId,
                    Map.of("status", AccountStatus.PENDING_ACTIVATION.name()),
                    Map.of(
                            "status",
                            AccountStatus.ACTIVE.name(),
                            "activatedMemberships",
                            activated));
        }

        UserAccount account = userAccountService.require(userId);
        List<MembershipView> memberships = membershipService.findActiveByUser(userId);
        if (memberships.isEmpty()) {
            throw AuthExceptions.noActiveMembership();
        }
        // §5.6: a resposta é a estrutura do login — o usuário já entra autenticado, evitando pedir
        // a senha logo depois de ele provar a posse do endereço.
        return openSession(account, memberships, metadata);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        String normalized = emailNormalizer.normalize(email);
        userAccountService
                .findByEmail(normalized)
                .filter(account -> !account.isEmailVerified())
                .ifPresent(
                        account -> {
                            var token =
                                    verificationTokenService.issue(
                                            account.id(),
                                            null,
                                            VerificationTokenType.EMAIL_VERIFICATION);
                            events.publish(
                                    new VerificationResentEvent(
                                            account.id(),
                                            account.email(),
                                            account.fullName(),
                                            token.rawToken()));
                        });
        // SG-01: nenhum retorno distingue os casos. Um e-mail inexistente, um já verificado e um
        // reenvio real produzem exatamente a mesma resposta.
    }

    @Override
    public List<TenantOptionResponse> listTenants(UUID userId) {
        return sessionAssembler.toOptions(tenantService.optionsFor(userId));
    }

    @Override
    @Transactional
    public SessionOutcome selectTenant(
            SelectTenantRequest request, UUID userId, String rawRefreshToken) {
        UUID tenantId = request.tenantId();
        MembershipView membership =
                membershipService
                        .findByTenantAndUser(tenantId, userId)
                        // ART-024: organização da qual não se é membro é indistinguível de
                        // inexistente. 403 confirmaria que o identificador existe.
                        .orElseThrow(() -> EntityNotFoundException.of(TenantView.class, tenantId));
        if (!membership.isActive()) {
            throw AuthExceptions.membershipInactive(); // RN-459 / DEVTIME-1102
        }

        TenantView tenant = tenantService.require(tenantId);
        if (tenant.status() == TenantState.CANCELLED) {
            throw AuthExceptions.tenantCancelled(); // RN-008 / DEVTIME-1202
        }
        // CX-08: organização suspensa é selecionável. O bloqueio de escrita ocorre por operação
        // (DEVTIME-1201), não na seleção — do contrário o titular não conseguiria nem exportar os
        // próprios dados durante a suspensão.

        UserAccount account = userAccountService.require(userId);
        // RT-07: a sessão passa a pertencer à organização escolhida, para que a suspensão do
        // vínculo alcance também as sessões abertas antes da seleção.
        refreshTokenService
                .identify(rawRefreshToken)
                .ifPresent(sessionId -> refreshTokenService.attachTenant(sessionId, tenantId));

        auditRecorder.record(
                "TENANT_SELECTED",
                AuthAuditRecorder.ENTITY_MEMBERSHIP,
                membership.id(),
                Map.of(),
                Map.of("tenantId", tenantId.toString(), "role", membership.role().name()));
        events.publish(new TenantSelectedEvent(userId, tenantId));

        return new SessionOutcome(
                sessionAssembler.withTenant(account, tenant, membership), rawRefreshToken);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        // RT-05: apenas a sessão corrente. Ausência de cookie não é erro — logout é idempotente e
        // recusar a chamada deixaria o cliente sem forma de limpar o estado local.
        refreshTokenService.identify(rawRefreshToken).ifPresent(refreshTokenService::revoke);
    }

    @Override
    @Transactional
    public void logoutAll(UUID userId) {
        int revoked = refreshTokenService.revokeAllOf(userId); // FA-12
        log.info("todas as sessões encerradas userId={} revokedCount={}", userId, revoked);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, UUID userId, String rawRefreshToken) {
        UserAccount account = userAccountService.require(userId);
        if (!userAccountService.matchesPassword(userId, request.currentPassword())) {
            throw AuthExceptions.currentPasswordIncorrect(); // PW-05 / DEVTIME-1011
        }
        if (userAccountService.matchesPassword(userId, request.newPassword())) {
            throw AuthExceptions.passwordUnchanged(); // DEVTIME-1012
        }
        passwordPolicyValidator.validate(request.newPassword()); // RN-451

        userAccountService.changePassword(userId, request.newPassword()); // TK-04

        // RN-454 / RT-06: derruba as demais sessões e preserva a corrente. Sem cookie válido —
        // caso de cliente que perdeu o cookie — todas caem, o que é o comportamento seguro.
        UUID currentSession = refreshTokenService.identify(rawRefreshToken).orElse(null);
        int revoked =
                currentSession == null
                        ? refreshTokenService.revokeAllOf(userId)
                        : refreshTokenService.revokeAllOfExcept(userId, currentSession);

        auditRecorder.record(
                "USER_PASSWORD_CHANGED",
                AuthAuditRecorder.ENTITY_USER,
                userId,
                Map.of(),
                // CP-11: nunca a senha nem o hash. Apenas o instante e o efeito sobre sessões.
                Map.of(
                        "passwordChangedAt",
                        clock.instant().toString(),
                        "revokedSessions",
                        revoked));
        events.publish(
                new PasswordChangedEvent(
                        userId, account.email(), account.fullName(), clock.instant()));
    }

    @Override
    public MeResponse me(UUID userId, UUID tenantId) {
        UserAccount account = userAccountService.require(userId);
        TenantView tenant = tenantService.require(tenantId);
        MembershipView membership =
                membershipService
                        .findByTenantAndUser(tenantId, userId)
                        .orElseThrow(AuthExceptions::membershipInactive);
        return meAssembler.assemble(account, tenant, membership, tenantService.optionsFor(userId));
    }

    /**
     * Abre a sessão conforme a quantidade de organizações (§9.3 do spec).
     *
     * <p>Uma organização ⇒ token com {@code tid} e sessão pronta, sem tela intermediária (CE-03).
     * Duas ou mais ⇒ token de pré-seleção (CE-P-11).
     */
    private SessionOutcome openSession(
            UserAccount account, List<MembershipView> memberships, RequestMetadata metadata) {
        boolean single = memberships.size() == 1;
        UUID tenantId = single ? memberships.get(0).tenantId() : null;
        var issued =
                refreshTokenService.issue(
                        account.id(), tenantId, metadata.userAgent(), metadata.ipAddress());

        if (!single) {
            return new SessionOutcome(
                    sessionAssembler.pendingTenantSelection(
                            account, tenantService.optionsFor(account.id())),
                    issued.rawToken());
        }
        MembershipView membership = memberships.get(0);
        TenantView tenant = tenantService.require(tenantId);
        assertTenantUsable(tenant);
        auditRecorder.record(
                "USER_LOGIN_SUCCEEDED",
                AuthAuditRecorder.ENTITY_USER,
                account.id(),
                Map.of(),
                Map.of("tenantId", tenantId.toString(), "role", membership.role().name()));
        return new SessionOutcome(
                sessionAssembler.withTenant(account, tenant, membership), issued.rawToken());
    }

    /** Passo 5 do login: a conta precisa estar utilizável. */
    private void assertUsable(UserAccount account) {
        if (account.status() == AccountStatus.DISABLED) {
            // Desativada por administrador. Responde como credencial inválida, e não com código
            // próprio: o estado da conta alheia não é informação que o solicitante deva obter
            // apenas por apresentar a senha certa.
            throw AuthExceptions.invalidCredentials();
        }
        if (!account.isEmailVerified()) {
            metrics.loginAttempt("unverified");
            throw AuthExceptions.emailNotVerified(); // DEVTIME-1008
        }
    }

    /** RN-008: organização cancelada não abre sessão. Suspensa abre, em modo leitura (RN-007). */
    private void assertTenantUsable(TenantView tenant) {
        if (tenant.status() == TenantState.CANCELLED) {
            throw AuthExceptions.tenantCancelled();
        }
    }
}
