package com.devtime.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Emissão, rotação, revogação e detecção de reuso de refresh token (RN-005, RT-01 a RT-08).
 *
 * <p>É o ponto de maior risco da feature (caminho crítico de T-001-25): um erro aqui ou encerra
 * sessões legítimas em massa, ou mantém viva uma sessão roubada.
 */
public interface RefreshTokenService {

    /** Sessão emitida. O valor bruto existe apenas neste retorno e vai para o cookie. */
    record IssuedSession(UUID id, String rawToken, Instant expiresAt) {}

    /** Sessão ativa, para {@code GET /auth/sessions}. */
    record ActiveSession(
            UUID id,
            String userAgent,
            String ipAddress,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt) {}

    /** Resultado da rotação: o token anterior e o novo (RT-03). */
    record RotatedSession(UUID previousId, UUID tenantId, UUID userId, IssuedSession issued) {}

    /**
     * Emite a sessão do login.
     *
     * @param tenantId nulo quando o usuário ainda precisa selecionar organização (§5.1 security.md)
     */
    IssuedSession issue(UUID userId, UUID tenantId, String userAgent, String ipAddress);

    /**
     * RT-03: rotaciona o token apresentado.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-1004} quando o token é
     *     desconhecido, revogado ou expirado; {@code DEVTIME-1005} quando já havia sido rotacionado
     *     — caso em que toda a cadeia do usuário é revogada (RN-005) antes de a exceção subir
     */
    RotatedSession rotate(String rawToken, String userAgent, String ipAddress);

    /**
     * Vincula a sessão corrente a uma organização, sem rotacionar.
     *
     * <p>Usado por {@code POST /auth/select-tenant}: o refresh continua o mesmo, mas passa a
     * pertencer à organização escolhida, para que RT-07 (revogar as sessões de um tenant ao
     * suspender o vínculo) alcance também as sessões abertas antes da seleção.
     */
    void attachTenant(UUID sessionId, UUID tenantId);

    /** Identifica a sessão apresentada no cookie, sem rotacionar nem lançar. */
    java.util.Optional<UUID> identify(String rawToken);

    /** RT-05: logout revoga apenas a sessão corrente. */
    void revoke(UUID sessionId);

    /** Revoga todas as sessões do usuário ({@code logout-all}, redefinição de senha). */
    int revokeAllOf(UUID userId);

    /** RN-454 / RT-06: revoga todas menos a corrente. */
    int revokeAllOfExcept(UUID userId, UUID keptSessionId);

    /** RT-07: suspensão ou remoção de vínculo derruba as sessões daquele tenant, e só delas. */
    int revokeAllOfInTenant(UUID userId, UUID tenantId);

    /** RN-008: cancelamento da organização derruba as sessões de todos os seus membros. */
    int revokeAllInTenant(UUID tenantId);

    /** Sessões vivas do usuário (§5.11). */
    List<ActiveSession> listActive(UUID userId);

    /**
     * OWN-09: revoga uma sessão do próprio usuário.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2002} / {@code 404}
     *     quando a sessão é de outro usuário — nunca {@code 403}, que confirmaria sua existência
     */
    void revokeOwned(UUID userId, UUID sessionId);

    /** RT-08: descarta tokens encerrados há mais de 30 dias (T-001-36). */
    int purgeSettled();
}
