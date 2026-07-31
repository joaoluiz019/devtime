package com.devtime.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.auth.AuthService.SessionOutcome;
import com.devtime.auth.dto.AuthRequests.LoginRequest;
import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.support.FixedClockTestConfiguration;
import com.devtime.user.domain.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rotação e detecção de reuso (TS-001-09, TS-001-10, AC-001-06, AC-001-31, AC-001-42).
 *
 * <p>O par de testes mais importante da feature: um prova que a cadeia é revogada no reuso
 * (RN-005), o outro prova que o logout <b>não</b> é confundido com roubo (CX-06). Errar qualquer um
 * dos dois produz sessões encerradas sem motivo ou sessões roubadas que continuam válidas.
 */
class RefreshTokenRotationIntegrationTest extends AuthTestSupport {

    @Test
    @DisplayName("AC-001-06: o refresh rotaciona o token e encadeia o anterior")
    void refreshMustRotateAndChain() {
        SessionOutcome session = authenticatedSession("rotacao");
        String first = session.rawRefreshToken();

        SessionOutcome renewed = authService.refresh(first, metadata());

        assertThat(renewed.rawRefreshToken()).isNotEqualTo(first);
        var previous = refreshTokenRepository.findByTokenHash(hash(first)).orElseThrow();
        assertThat(previous.getReplacedById()).as("RT-03").isNotNull();
        assertThat(previous.isRevoked()).isTrue();
        assertThat(renewed.response().accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("RN-005: reuso de token rotacionado revoga toda a cadeia com DEVTIME-1005")
    void reusingRotatedTokenMustRevokeWholeChain() {
        SessionOutcome session = authenticatedSession("reuso");
        String r1 = session.rawRefreshToken();
        String r2 = authService.refresh(r1, metadata()).rawRefreshToken();
        String r3 = authService.refresh(r2, metadata()).rawRefreshToken();

        assertThatThrownBy(() -> authService.refresh(r1, metadata()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);

        assertThat(refreshTokenRepository.findByTokenHash(hash(r3)).orElseThrow().isRevoked())
                .as("a revogação em cadeia precisa sobreviver ao 401 que a acompanha")
                .isTrue();
        assertThatThrownBy(() -> authService.refresh(r3, metadata()))
                .as("AC-001-31: o token mais recente da cadeia também deixa de renovar")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("CX-06: token revogado por logout devolve DEVTIME-1004, não reuso")
    void revokedTokenMustNotBeTreatedAsReuse() {
        SessionOutcome session = authenticatedSession("logout");
        String token = session.rawRefreshToken();
        authService.logout(token);

        assertThatThrownBy(() -> authService.refresh(token, metadata()))
                .as("tratar logout como roubo encerraria todas as sessões a cada cookie antigo")
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("AC-001-07: o logout revoga apenas a sessão corrente")
    void logoutMustRevokeOnlyCurrentSession() {
        RegisterRequest request = registerRequest("logout-uma");
        UUID userId = registerAndVerify(request);
        String sessionA =
                authService
                        .login(new LoginRequest(request.email(), VALID_PASSWORD), metadata())
                        .rawRefreshToken();
        String sessionB =
                authService
                        .login(new LoginRequest(request.email(), VALID_PASSWORD), metadata())
                        .rawRefreshToken();

        authService.logout(sessionA);

        assertThat(refreshTokenRepository.findByTokenHash(hash(sessionB)).orElseThrow().isRevoked())
                .isFalse();
        assertThat(refreshTokenService.listActive(userId)).hasSize(1);
    }

    @Test
    @DisplayName("FA-12: logout-all revoga todas as sessões, inclusive a corrente")
    void logoutAllMustRevokeEverySession() {
        RegisterRequest request = registerRequest("logout-todas");
        UUID userId = registerAndVerify(request);
        authService.login(new LoginRequest(request.email(), VALID_PASSWORD), metadata());
        authService.login(new LoginRequest(request.email(), VALID_PASSWORD), metadata());

        authService.logoutAll(userId);

        assertThat(refreshTokenService.listActive(userId)).isEmpty();
    }

    @Test
    @DisplayName("Cookie ausente ou desconhecido devolve DEVTIME-1004, sem revogar nada")
    void unknownTokenMustBeRejectedWithoutSideEffects() {
        assertThatThrownBy(() -> authService.refresh("token-inexistente", metadata()))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    private SessionOutcome authenticatedSession(String prefix) {
        RegisterRequest request = registerRequest(prefix);
        registerAndVerify(request);
        return authService.login(new LoginRequest(request.email(), VALID_PASSWORD), metadata());
    }

    private UUID registerAndVerify(RegisterRequest request) {
        var registered = authService.register(request, metadata());
        transactionTemplate.executeWithoutResult(
                status -> {
                    var user = userRepository.findById(registered.userId()).orElseThrow();
                    user.setStatus(UserStatus.ACTIVE);
                    user.setEmailVerifiedAt(FixedClockTestConfiguration.FIXED_INSTANT);
                    userRepository.save(user);
                });
        return registered.userId();
    }

    private String hash(String rawToken) {
        return new OpaqueTokenGenerator().hash(rawToken);
    }
}
