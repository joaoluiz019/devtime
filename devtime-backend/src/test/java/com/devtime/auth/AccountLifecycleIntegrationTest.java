package com.devtime.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.auth.dto.AuthRequests.ChangePasswordRequest;
import com.devtime.auth.dto.AuthRequests.LoginRequest;
import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.auth.dto.AuthResponses.RegisterResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.support.FixedClockTestConfiguration;
import com.devtime.user.domain.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verificação de e-mail e ciclo de senha (TS-001-11, TS-001-12, AC-001-02, AC-001-08 a AC-001-09,
 * AC-001-17 a AC-001-18, AC-001-26 a AC-001-27, AC-001-32).
 */
class AccountLifecycleIntegrationTest extends AuthTestSupport {

    private static final String NEW_PASSWORD = "OutraSenha789";

    @Test
    @DisplayName("AC-001-02: a verificação ativa a conta e devolve sessão pronta")
    void verificationMustActivateAccountAndOpenSession() {
        RegisterRequest request = registerRequest("verifica");
        RegisterResponse registered = authService.register(request, metadata());
        String token = verificationTokenOf(registered.userId());

        var outcome = authService.verifyEmail(token, metadata());

        var user = userRepository.findById(registered.userId()).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(outcome.response().accessToken()).isNotBlank();
        assertThat(outcome.response().tenantSelectionRequired()).isFalse();
    }

    @Test
    @DisplayName(
            "CE-AU-04/CA-08: a verificação é idempotente — o segundo clique também responde ok")
    void verificationMustBeIdempotent() {
        RegisterRequest request = registerRequest("verifica-2x");
        RegisterResponse registered = authService.register(request, metadata());
        String token = verificationTokenOf(registered.userId());
        authService.verifyEmail(token, metadata());
        var firstVerifiedAt =
                userRepository.findById(registered.userId()).orElseThrow().getEmailVerifiedAt();

        assertThatCode(() -> authService.verifyEmail(token, metadata()))
                .as("clientes de e-mail com pré-visualização consomem o link antes do usuário")
                .doesNotThrowAnyException();
        assertThat(userRepository.findById(registered.userId()).orElseThrow().getEmailVerifiedAt())
                .as("AC-001-26: emailVerifiedAt não é alterado na segunda chamada")
                .isEqualTo(firstVerifiedAt);
    }

    @Test
    @DisplayName("§5.6: token de verificação desconhecido devolve DEVTIME-1010")
    void unknownVerificationTokenMustReturnNotFound() {
        assertThatThrownBy(() -> authService.verifyEmail("token-inexistente", metadata()))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_TOKEN_INVALID);
    }

    @Test
    @DisplayName("AC-001-17: token de verificação expirado devolve DEVTIME-1009")
    void expiredVerificationTokenMustReturnGone() {
        RegisterRequest request = registerRequest("verifica-expirado");
        RegisterResponse registered = authService.register(request, metadata());
        String token = verificationTokenOf(registered.userId());
        expire(registered.userId(), VerificationTokenType.EMAIL_VERIFICATION);

        assertThatThrownBy(() -> authService.verifyEmail(token, metadata()))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        assertThat(userRepository.findById(registered.userId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING_ACTIVATION);
    }

    @Test
    @DisplayName("RN-457: o reenvio invalida o token anterior")
    void resendMustInvalidatePreviousToken() {
        RegisterRequest request = registerRequest("reenvio");
        RegisterResponse registered = authService.register(request, metadata());
        String first = verificationTokenOf(registered.userId());

        authService.resendVerification(request.email());
        String second = verificationTokenOf(registered.userId());

        assertThat(second).isNotEqualTo(first);
        assertThatThrownBy(() -> authService.verifyEmail(first, metadata()))
                .as("dois links válidos ao mesmo tempo tornariam a revogação impraticável")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("SG-02/AC-001-32: 'esqueci a senha' não revela a existência da conta")
    void forgotPasswordMustNotRevealAccountExistence() {
        RegisterRequest request = registerRequest("esqueci");
        authService.register(request, metadata());

        assertThatCode(() -> passwordResetService.requestReset(request.email()))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                passwordResetService.requestReset(
                                        "naoexiste-" + UUID.randomUUID() + "@exemplo.com"))
                .as("as duas chamadas precisam ser indistinguíveis para quem observa de fora")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-001-08: a redefinição troca a senha e revoga todas as sessões")
    void resetPasswordMustReplacePasswordAndRevokeSessions() {
        RegisterRequest request = registerRequest("redefine");
        UUID userId = registerAndVerify(request);
        authService.login(new LoginRequest(request.email(), VALID_PASSWORD), metadata());
        passwordResetService.requestReset(request.email());
        String token = verificationTokenOf(userId, VerificationTokenType.PASSWORD_RESET);

        passwordResetService.resetPassword(token, NEW_PASSWORD);

        assertThat(refreshTokenService.listActive(userId))
                .as("CE-AU-05: todas as sessões caem, inclusive a que pediu a redefinição")
                .isEmpty();
        assertThatCode(
                        () ->
                                authService.login(
                                        new LoginRequest(request.email(), NEW_PASSWORD),
                                        metadata()))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                authService.login(
                                        new LoginRequest(request.email(), VALID_PASSWORD),
                                        metadata()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("AC-001-18/RN-461: token de redefinição reutilizado devolve DEVTIME-1007")
    void resetTokenMustBeSingleUse() {
        RegisterRequest request = registerRequest("redefine-2x");
        UUID userId = registerAndVerify(request);
        passwordResetService.requestReset(request.email());
        String token = verificationTokenOf(userId, VerificationTokenType.PASSWORD_RESET);
        passwordResetService.resetPassword(token, NEW_PASSWORD);

        assertThatThrownBy(() -> passwordResetService.resetPassword(token, "TerceiraSenha11"))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }

    @Test
    @DisplayName("AC-001-27/CX-07: a redefinição desbloqueia a conta e zera as falhas")
    void resetMustUnlockLockedAccount() {
        RegisterRequest request = registerRequest("redefine-bloqueada");
        UUID userId = registerAndVerify(request);
        passwordResetService.requestReset(request.email());
        String token = verificationTokenOf(userId, VerificationTokenType.PASSWORD_RESET);
        for (int attempt = 1; attempt <= 5; attempt++) {
            catchFailure(
                    () ->
                            authService.login(
                                    new LoginRequest(request.email(), "SenhaErrada999"),
                                    metadata()));
        }

        passwordResetService.resetPassword(token, NEW_PASSWORD);

        var user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName(
            "RN-454/AC-001-09: a troca de senha preserva a sessão corrente e derruba as demais")
    void changePasswordMustKeepCurrentSessionOnly() {
        RegisterRequest request = registerRequest("troca");
        UUID userId = registerAndVerify(request);
        String sessionA =
                authService
                        .login(new LoginRequest(request.email(), VALID_PASSWORD), metadata())
                        .rawRefreshToken();
        authService.login(new LoginRequest(request.email(), VALID_PASSWORD), metadata());

        authService.changePassword(
                new ChangePasswordRequest(VALID_PASSWORD, NEW_PASSWORD), userId, sessionA);

        assertThat(refreshTokenService.listActive(userId)).hasSize(1);
        assertThatCode(() -> authService.refresh(sessionA, metadata()))
                .as("a sessão que trocou a senha continua válida")
                .doesNotThrowAnyException();
        assertThat(userRepository.findById(userId).orElseThrow().getPasswordChangedAt())
                .isEqualTo(FixedClockTestConfiguration.FIXED_INSTANT);
    }

    @Test
    @DisplayName("§5.9: senha atual incorreta devolve DEVTIME-1011")
    void changePasswordMustRequireCurrentPassword() {
        RegisterRequest request = registerRequest("troca-errada");
        UUID userId = registerAndVerify(request);

        assertThatThrownBy(
                        () ->
                                authService.changePassword(
                                        new ChangePasswordRequest("SenhaErrada999", NEW_PASSWORD),
                                        userId,
                                        null))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_INCORRECT);
    }

    @Test
    @DisplayName("§5.9: nova senha igual à atual devolve DEVTIME-1012")
    void changePasswordMustRejectUnchangedPassword() {
        RegisterRequest request = registerRequest("troca-igual");
        UUID userId = registerAndVerify(request);

        assertThatThrownBy(
                        () ->
                                authService.changePassword(
                                        new ChangePasswordRequest(VALID_PASSWORD, VALID_PASSWORD),
                                        userId,
                                        null))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_UNCHANGED);
    }

    private UUID registerAndVerify(RegisterRequest request) {
        RegisterResponse registered = authService.register(request, metadata());
        authService.verifyEmail(verificationTokenOf(registered.userId()), metadata());
        return registered.userId();
    }

    /**
     * Recupera o valor bruto do token emitido.
     *
     * <p>Só o hash é persistido (RT-02), então o teste captura o valor pelo evento publicado — o
     * mesmo caminho que o e-mail percorre. Ler a coluna não funcionaria, e reconstruir o token
     * seria provar que o hash é reversível, o que ele não é.
     */
    private String verificationTokenOf(UUID userId) {
        return verificationTokenOf(userId, VerificationTokenType.EMAIL_VERIFICATION);
    }

    private String verificationTokenOf(UUID userId, VerificationTokenType type) {
        return CapturedAuthEvents.tokenFor(userId, type);
    }

    /**
     * Antecipa o vencimento do token para simular a passagem do tempo.
     *
     * <p>Por SQL, e não por builder (BR-207), por duas razões que se somam: {@code expiresAt} é
     * imutável na entidade — como deve ser, já que a validade de um token emitido não se altera — e
     * o relógio dos testes é fixo (BR-205), então não há como avançá-lo. O que se simula aqui é a
     * passagem de sete dias, não a criação de um registro.
     */
    private void expire(UUID userId, VerificationTokenType type) {
        // OffsetDateTime e não Timestamp: pgjdbc converte Timestamp usando o fuso padrão da JVM,
        // o que deslocaria o instante e poderia deixá-lo no futuro em fusos negativos.
        // Transação explícita: o pool roda com auto-commit desligado, então um UPDATE solto por
        // JdbcTemplate jamais seria confirmado — e o teste passaria a verificar o estado errado.
        Integer updated =
                transactionTemplate.execute(
                        status ->
                                new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                                        .update(
                                                "UPDATE verification_tokens SET expires_at = ?"
                                                        + " WHERE user_id = ? AND type = ?",
                                                FixedClockTestConfiguration.FIXED_INSTANT
                                                        .minusSeconds(60)
                                                        .atOffset(java.time.ZoneOffset.UTC),
                                                userId,
                                                type.name()));
        assertThat(updated).as("o cenário exige um token efetivamente vencido").isPositive();
    }

    private void catchFailure(Runnable action) {
        org.assertj.core.api.Assertions.catchThrowable(action::run);
    }
}
