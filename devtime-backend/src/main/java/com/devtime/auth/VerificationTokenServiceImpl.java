package com.devtime.auth;

import com.devtime.auth.domain.AuthExceptions;
import com.devtime.auth.domain.VerificationToken;
import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.shared.error.BusinessRuleException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link VerificationTokenService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationTokenServiceImpl implements VerificationTokenService {

    /** §4.2 de state-machines.md e RN-457. */
    static final Duration VERIFICATION_TTL = Duration.ofDays(7);

    /** RN-461 / PW-06. */
    static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    /** Carência antes de descartar tokens já vencidos, para diagnóstico de suporte. */
    private static final Duration CLEANUP_GRACE = Duration.ofDays(1);

    /**
     * Códigos de erro por tipo de token.
     *
     * <p>Um mapa, e não um {@code switch} espalhado pelos métodos: {@code authentication.md} exige
     * códigos distintos por fluxo, e concentrá-los aqui torna a divergência visível de imediato.
     */
    private static final Map<VerificationTokenType, TokenErrors> ERRORS =
            Map.of(
                    VerificationTokenType.EMAIL_VERIFICATION,
                            new TokenErrors(
                                    AuthExceptions::verificationTokenInvalid,
                                    AuthExceptions::verificationTokenExpired),
                    VerificationTokenType.PASSWORD_RESET,
                            new TokenErrors(
                                    AuthExceptions::passwordResetTokenInvalid,
                                    AuthExceptions::passwordResetTokenInvalid),
                    VerificationTokenType.INVITATION,
                            new TokenErrors(
                                    AuthExceptions::invitationInvalid,
                                    AuthExceptions::invitationExpired));

    private final VerificationTokenRepository repository;
    private final OpaqueTokenGenerator tokenGenerator;
    private final Clock clock;

    @Override
    @Transactional
    public IssuedToken issue(UUID userId, UUID tenantId, VerificationTokenType type) {
        Instant now = clock.instant();
        // RN-457: emitir um novo token invalida o anterior. Sem isso, um convite reenviado deixaria
        // dois links válidos, e revogar o acesso exigiria caçar ambos.
        repository.invalidatePrevious(userId, type, now);

        String rawToken = tokenGenerator.generate();
        VerificationToken token = new VerificationToken();
        token.setUserId(userId);
        token.setTenantId(tenantId);
        token.setType(type);
        token.setTokenHash(tokenGenerator.hash(rawToken));
        token.setExpiresAt(now.plus(ttlOf(type)));
        VerificationToken saved = repository.save(token);
        return new IssuedToken(saved.getId(), rawToken, saved.getExpiresAt());
    }

    @Override
    @Transactional
    public ConsumedToken consume(String rawToken, VerificationTokenType type) {
        VerificationToken token = requireUsable(rawToken, type);
        // SG-13: marcado na mesma transação do efeito. Consumir depois abriria janela para uso
        // duplo sob concorrência.
        token.setConsumedAt(clock.instant());
        return toConsumed(token);
    }

    @Override
    public ConsumedToken peek(String rawToken, VerificationTokenType type) {
        return toConsumed(requireUsable(rawToken, type));
    }

    @Override
    public Optional<ConsumedToken> findConsumed(String rawToken, VerificationTokenType type) {
        return find(rawToken, type)
                .filter(VerificationToken::isConsumed)
                // Substituído por reenvio não vale como "já usado": responder sucesso levaria o
                // usuário a acreditar que o link antigo funcionou (RN-457).
                .filter(token -> !token.isInvalidated())
                .filter(token -> !token.isExpiredAt(clock.instant()))
                .map(this::toConsumed);
    }

    @Override
    @Transactional
    public int purgeSettled() {
        Instant now = clock.instant();
        return repository.softDeleteSettledBefore(now.minus(CLEANUP_GRACE), now);
    }

    private VerificationToken requireUsable(String rawToken, VerificationTokenType type) {
        TokenErrors errors = ERRORS.get(type);
        VerificationToken token = find(rawToken, type).orElseThrow(() -> errors.invalid().get());
        if (token.isSettledAt(clock.instant())) {
            throw errors.expired().get();
        }
        return token;
    }

    private Optional<VerificationToken> find(String rawToken, VerificationTokenType type) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHashAndType(tokenGenerator.hash(rawToken), type);
    }

    private ConsumedToken toConsumed(VerificationToken token) {
        return new ConsumedToken(
                token.getId(), token.getUserId(), token.getTenantId(), token.getExpiresAt());
    }

    private Duration ttlOf(VerificationTokenType type) {
        return type == VerificationTokenType.PASSWORD_RESET ? PASSWORD_RESET_TTL : VERIFICATION_TTL;
    }

    private record TokenErrors(
            Supplier<BusinessRuleException> invalid, Supplier<BusinessRuleException> expired) {}
}
