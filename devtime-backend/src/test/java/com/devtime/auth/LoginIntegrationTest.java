package com.devtime.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.auth.AuthService.SessionOutcome;
import com.devtime.auth.dto.AuthRequests.LoginRequest;
import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.auth.dto.AuthResponses.RegisterResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.security.JwtService;
import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import com.devtime.user.domain.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Login (TS-001-07, TS-001-08, AC-001-03, AC-001-14 a AC-001-16, AC-001-33).
 *
 * <p>A ordem das sete verificações da §6.1 é normativa (BR-062). Cada teste isola um passo e prova
 * que ele ocorre <b>antes</b> do seguinte — é a ordem, e não cada verificação isolada, que fecha os
 * canais de enumeração.
 */
class LoginIntegrationTest extends AuthTestSupport {

    @Autowired private JwtService jwtService;
    @Autowired private LoginAttemptService loginAttemptService;
    @Autowired private com.devtime.user.UserAccountService userAccountService;

    @Test
    @DisplayName("AC-001-03: login com um único tenant devolve sessão pronta, com claim tid")
    void singleTenantLoginMustReturnReadySession() {
        RegisterRequest request = registerRequest("login-ok");
        RegisterResponse registered = authService.register(request, metadata());
        verifyEmailOf(registered.userId());

        SessionOutcome outcome =
                authService.login(new LoginRequest(request.email(), VALID_PASSWORD), metadata());

        assertThat(outcome.response().tenantSelectionRequired()).isFalse();
        assertThat(outcome.response().expiresIn()).isEqualTo(900);
        assertThat(outcome.response().tokenType()).isEqualTo("Bearer");
        assertThat(outcome.response().role()).isEqualTo(Role.OWNER);
        assertThat(outcome.response().permissions())
                .as("TK-03: as permissões são derivadas do papel e viajam no corpo, não no token")
                .contains(Permission.TENANT_VIEW.name());
        assertThat(outcome.rawRefreshToken()).isNotBlank();

        var claims = jwtService.parse(outcome.response().accessToken());
        assertThat(claims.tenantId()).isEqualTo(registered.tenantId());
        assertThat(claims.role()).isEqualTo(Role.OWNER);

        var user = userRepository.findById(registered.userId()).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("AC-001-33/SG-03: e-mail inexistente e senha errada produzem o mesmo DEVTIME-1001")
    void unknownEmailAndWrongPasswordMustBeIndistinguishable() {
        RegisterRequest request = registerRequest("login-enum");
        RegisterResponse registered = authService.register(request, metadata());
        verifyEmailOf(registered.userId());

        BusinessRuleException unknownEmail =
                catchAuthFailure(
                        () ->
                                authService.login(
                                        new LoginRequest(
                                                "inexistente-" + UUID.randomUUID() + "@exemplo.com",
                                                VALID_PASSWORD),
                                        metadata()));
        BusinessRuleException wrongPassword =
                catchAuthFailure(
                        () ->
                                authService.login(
                                        new LoginRequest(request.email(), "SenhaErrada999"),
                                        metadata()));

        assertThat(unknownEmail.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
        assertThat(wrongPassword.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
        assertThat(unknownEmail.getDetails())
                .as("qualquer campo extra reabriria o canal de enumeração")
                .isEqualTo(wrongPassword.getDetails())
                .isEmpty();
    }

    @Test
    @DisplayName("AC-001-14: senha incorreta incrementa failedLoginAttempts")
    void wrongPasswordMustIncrementFailureCounter() {
        RegisterRequest request = registerRequest("login-contador");
        RegisterResponse registered = authService.register(request, metadata());
        verifyEmailOf(registered.userId());

        catchAuthFailure(
                () ->
                        authService.login(
                                new LoginRequest(request.email(), "SenhaErrada999"), metadata()));

        assertThat(
                        userRepository
                                .findById(registered.userId())
                                .orElseThrow()
                                .getFailedLoginAttempts())
                .isEqualTo((short) 1);
    }

    @Test
    @DisplayName("AC-001-15: login sem e-mail verificado devolve DEVTIME-1008 e nenhum token")
    void unverifiedEmailMustBeRejectedAfterPasswordCheck() {
        RegisterRequest request = registerRequest("login-pendente");
        authService.register(request, metadata());

        assertThat(
                        catchAuthFailure(
                                        () ->
                                                authService.login(
                                                        new LoginRequest(
                                                                request.email(), VALID_PASSWORD),
                                                        metadata()))
                                .getErrorCode())
                .as("a verificação de e-mail vem DEPOIS da senha, para não revelar cadastros")
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("RN-453: 5 falhas em 15 minutos bloqueiam a conta por 30 minutos")
    void fiveFailuresMustLockTheAccount() {
        RegisterRequest request = registerRequest("login-bloqueio");
        RegisterResponse registered = authService.register(request, metadata());
        verifyEmailOf(registered.userId());

        for (int attempt = 1; attempt <= 5; attempt++) {
            catchAuthFailure(
                    () ->
                            authService.login(
                                    new LoginRequest(request.email(), "SenhaErrada999"),
                                    metadata()));
        }

        var locked = userRepository.findById(registered.userId()).orElseThrow();
        assertThat(locked.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(locked.getLockedUntil())
                .as("RN-453: bloqueio de 30 minutos")
                .isEqualTo(NOW_PLUS_30_MIN);

        assertThat(
                        catchAuthFailure(
                                        () ->
                                                authService.login(
                                                        new LoginRequest(
                                                                request.email(), VALID_PASSWORD),
                                                        metadata()))
                                .getErrorCode())
                .as("AC-001-16: mesmo com a senha CORRETA, a conta bloqueada devolve 423")
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("RN-453: o bloqueio precede a verificação de senha, sem servir de oráculo")
    void lockMustBeCheckedBeforePassword() {
        RegisterRequest request = registerRequest("login-ordem");
        RegisterResponse registered = authService.register(request, metadata());
        verifyEmailOf(registered.userId());
        for (int attempt = 1; attempt <= 5; attempt++) {
            catchAuthFailure(
                    () ->
                            authService.login(
                                    new LoginRequest(request.email(), "SenhaErrada999"),
                                    metadata()));
        }

        ErrorCode withCorrectPassword =
                catchAuthFailure(
                                () ->
                                        authService.login(
                                                new LoginRequest(request.email(), VALID_PASSWORD),
                                                metadata()))
                        .getErrorCode();
        ErrorCode withWrongPassword =
                catchAuthFailure(
                                () ->
                                        authService.login(
                                                new LoginRequest(request.email(), "OutraErrada99"),
                                                metadata()))
                        .getErrorCode();

        assertThat(withCorrectPassword)
                .isEqualTo(withWrongPassword)
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("RN-453: login bem-sucedido zera o contador de falhas")
    void successfulLoginMustResetFailureCounter() {
        RegisterRequest request = registerRequest("login-reset");
        RegisterResponse registered = authService.register(request, metadata());
        verifyEmailOf(registered.userId());
        for (int attempt = 1; attempt <= 4; attempt++) {
            catchAuthFailure(
                    () ->
                            authService.login(
                                    new LoginRequest(request.email(), "SenhaErrada999"),
                                    metadata()));
        }

        authService.login(new LoginRequest(request.email(), VALID_PASSWORD), metadata());

        assertThat(
                        userRepository
                                .findById(registered.userId())
                                .orElseThrow()
                                .getFailedLoginAttempts())
                .isZero();
    }

    @Test
    @DisplayName("§11 de spec 001: o bloqueio vencido é desfeito no próprio login")
    void expiredLockMustBeReleasedOnLogin() {
        RegisterRequest request = registerRequest("login-desbloqueio");
        RegisterResponse registered = authService.register(request, metadata());
        verifyEmailOf(registered.userId());
        for (int attempt = 1; attempt <= 5; attempt++) {
            catchAuthFailure(
                    () ->
                            authService.login(
                                    new LoginRequest(request.email(), "SenhaErrada999"),
                                    metadata()));
        }

        // O relógio dos testes é fixo (BR-205); em vez de avançá-lo, o prazo é trazido para o
        // passado, que é o mesmo estado observável de "bloqueio vencido".
        transactionTemplate.executeWithoutResult(
                status -> {
                    var user = userRepository.findById(registered.userId()).orElseThrow();
                    user.setLockedUntil(NOW_PLUS_30_MIN.minusSeconds(3_600));
                    userRepository.save(user);
                });

        var account = userAccountService.require(registered.userId());
        var released = loginAttemptService.assertNotLocked(account);

        assertThat(released.lockedUntil()).isNull();
        assertThat(userRepository.findById(registered.userId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    private static final java.time.Instant NOW_PLUS_30_MIN =
            com.devtime.support.FixedClockTestConfiguration.FIXED_INSTANT.plusSeconds(1_800);

    private void verifyEmailOf(UUID userId) {
        transactionTemplate.executeWithoutResult(
                status -> {
                    var user = userRepository.findById(userId).orElseThrow();
                    user.setStatus(UserStatus.ACTIVE);
                    user.setEmailVerifiedAt(
                            com.devtime.support.FixedClockTestConfiguration.FIXED_INSTANT);
                    userRepository.save(user);
                });
    }

    private BusinessRuleException catchAuthFailure(Runnable action) {
        return (BusinessRuleException)
                org.assertj.core.api.Assertions.catchThrowable(
                        () -> {
                            action.run();
                            throw new AssertionError("esperava falha de autenticação");
                        });
    }
}
