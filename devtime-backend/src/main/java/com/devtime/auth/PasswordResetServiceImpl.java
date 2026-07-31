package com.devtime.auth;

import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.auth.event.AuthEvents.PasswordChangedEvent;
import com.devtime.auth.event.AuthEvents.PasswordResetRequestedEvent;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.user.UserAccountService;
import com.devtime.user.dto.UserAccount;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link PasswordResetService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserAccountService userAccountService;
    private final VerificationTokenService verificationTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final EmailNormalizer emailNormalizer;
    private final AuthAuditRecorder auditRecorder;
    private final DomainEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional
    public void requestReset(String email) {
        String normalized = emailNormalizer.normalize(email);
        userAccountService
                .findByEmail(normalized)
                // Conta ainda não verificada não recebe link de redefinição: o caminho dela é a
                // verificação, e emitir os dois links criaria duas rotas para ativar a mesma conta.
                .filter(UserAccount::isEmailVerified)
                .ifPresent(
                        account -> {
                            var token =
                                    verificationTokenService.issue(
                                            account.id(),
                                            null,
                                            VerificationTokenType.PASSWORD_RESET);
                            events.publish(
                                    new PasswordResetRequestedEvent(
                                            account.id(),
                                            account.email(),
                                            account.fullName(),
                                            token.rawToken()));
                        });
        // PW-07 / SG-02: nenhum retorno, nenhuma exceção, nenhum ramo observável. O controller
        // responde 202 em todos os casos.
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        passwordPolicyValidator.validate(newPassword); // RN-451, antes de consumir o token
        var consumed =
                verificationTokenService.consume(rawToken, VerificationTokenType.PASSWORD_RESET);

        UserAccount account = userAccountService.require(consumed.userId());
        // CX-07: a redefinição desbloqueia a conta e zera o contador de falhas.
        userAccountService.changePassword(account.id(), newPassword);

        // CE-AU-05: todas as sessões caem, inclusive a que pediu a redefinição. Quem redefine a
        // senha normalmente o faz por suspeitar de acesso indevido; preservar sessões contrariaria
        // a intenção da operação.
        int revoked = refreshTokenService.revokeAllOf(account.id());

        auditRecorder.record(
                "USER_PASSWORD_RESET",
                AuthAuditRecorder.ENTITY_USER,
                account.id(),
                Map.of(),
                Map.of(
                        "passwordChangedAt",
                        clock.instant().toString(),
                        "revokedSessions",
                        revoked));
        log.info("senha redefinida userId={} revokedSessions={}", account.id(), revoked);
        events.publish(
                new PasswordChangedEvent(
                        account.id(), account.email(), account.fullName(), clock.instant()));
    }
}
