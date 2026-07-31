package com.devtime.auth;

import com.devtime.auth.domain.AuthExceptions;
import com.devtime.auth.domain.RefreshToken;
import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.error.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link RefreshTokenService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenServiceImpl implements RefreshTokenService {

    /** RT-08: carência antes de descartar tokens já encerrados. */
    private static final java.time.Duration PURGE_GRACE = java.time.Duration.ofDays(30);

    /** Tamanho máximo aceito de {@code User-Agent}, conforme a coluna de V005. */
    private static final int USER_AGENT_MAX = 400;

    private final RefreshTokenRepository repository;
    private final TokenReuseRevocationService reuseRevocation;
    private final OpaqueTokenGenerator tokenGenerator;
    private final DevTimeProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public IssuedSession issue(UUID userId, UUID tenantId, String userAgent, String ipAddress) {
        return persistNew(userId, tenantId, userAgent, ipAddress);
    }

    @Override
    @Transactional
    public RotatedSession rotate(String rawToken, String userAgent, String ipAddress) {
        RefreshToken current =
                find(rawToken).orElseThrow(AuthExceptions::refreshTokenInvalid); // DEVTIME-1004

        // A ordem importa: "rotacionado" é verificado ANTES de "revogado" porque a rotação também
        // revoga o token anterior. Invertida, todo reuso seria classificado como logout (CX-06) e
        // RN-005 nunca dispararia.
        if (current.isRotated()) {
            int revoked = reuseRevocation.revokeChainOf(current.getUserId()); // commit próprio
            assert revoked >= 0;
            throw AuthExceptions.refreshTokenReuseDetected(); // DEVTIME-1005
        }
        if (current.isRevoked() || !clock.instant().isBefore(current.getExpiresAt())) {
            // CX-06: token revogado por logout não é reuso. Tratá-lo como roubo encerraria todas as
            // sessões do usuário toda vez que um cookie antigo fosse reapresentado.
            throw AuthExceptions.refreshTokenInvalid(); // DEVTIME-1004
        }

        IssuedSession issued =
                persistNew(current.getUserId(), current.getTenantId(), userAgent, ipAddress);
        current.setReplacedById(issued.id()); // RT-03: encadeia
        current.setRevokedAt(clock.instant());
        return new RotatedSession(
                current.getId(), current.getTenantId(), current.getUserId(), issued);
    }

    @Override
    @Transactional
    public void attachTenant(UUID sessionId, UUID tenantId) {
        repository.findById(sessionId).ifPresent(token -> token.setTenantId(tenantId));
    }

    @Override
    public Optional<UUID> identify(String rawToken) {
        return find(rawToken)
                .filter(token -> !token.isRevoked() && !token.isRotated())
                .map(RefreshToken::getId);
    }

    @Override
    @Transactional
    public void revoke(UUID sessionId) {
        repository
                .findById(sessionId)
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(clock.instant())); // RT-05
    }

    @Override
    @Transactional
    public int revokeAllOf(UUID userId) {
        return repository.revokeAllByUserId(userId, clock.instant());
    }

    @Override
    @Transactional
    public int revokeAllOfExcept(UUID userId, UUID keptSessionId) {
        return repository.revokeAllByUserIdExcept(userId, keptSessionId, clock.instant()); // RN-454
    }

    @Override
    public List<ActiveSession> listActive(UUID userId) {
        return repository.findLiveByUserId(userId, clock.instant()).stream()
                .map(
                        token ->
                                new ActiveSession(
                                        token.getId(),
                                        token.getUserAgent(),
                                        token.getIpAddress(),
                                        token.getCreatedAt(),
                                        // A entidade não possui lastUsedAt: `updatedAt` cumpre o
                                        // papel, porque as únicas escritas em um token vivo são a
                                        // vinculação de tenant e a revogação. Acrescentar coluna
                                        // exigiria migration para um dado que já existe.
                                        token.getUpdatedAt(),
                                        token.getExpiresAt()))
                .toList();
    }

    @Override
    @Transactional
    public void revokeOwned(UUID userId, UUID sessionId) {
        RefreshToken token =
                repository
                        .findById(sessionId)
                        // OWN-09 / ART-024: sessão de outro usuário é indistinguível de
                        // inexistente. Retornar 403 confirmaria que o identificador existe.
                        .filter(candidate -> candidate.getUserId().equals(userId))
                        .orElseThrow(
                                () -> EntityNotFoundException.of(RefreshToken.class, sessionId));
        if (!token.isRevoked()) {
            token.setRevokedAt(clock.instant());
        }
    }

    @Override
    @Transactional
    public int purgeSettled() {
        Instant now = clock.instant();
        return repository.softDeleteSettledBefore(now.minus(PURGE_GRACE), now); // RT-08
    }

    private IssuedSession persistNew(
            UUID userId, UUID tenantId, String userAgent, String ipAddress) {
        Instant now = clock.instant();
        String rawToken = tokenGenerator.generate();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTenantId(tenantId);
        token.setTokenHash(tokenGenerator.hash(rawToken)); // RT-02
        token.setExpiresAt(now.plus(properties.security().refreshTokenTtl()));
        token.setUserAgent(truncate(userAgent));
        token.setIpAddress(ipAddress);
        RefreshToken saved = repository.save(token);
        return new IssuedSession(saved.getId(), rawToken, saved.getExpiresAt());
    }

    private Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(tokenGenerator.hash(rawToken));
    }

    /**
     * Trunca em vez de recusar: um {@code User-Agent} longo demais é um dado de diagnóstico, e
     * recusar o login por causa dele transformaria um detalhe de telemetria em falha de acesso.
     */
    private String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= USER_AGENT_MAX
                ? userAgent
                : userAgent.substring(0, USER_AGENT_MAX);
    }
}
