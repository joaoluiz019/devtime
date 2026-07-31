package com.devtime.auth;

import com.devtime.auth.dto.AuthResponses.ActiveSessionListResponse;
import java.util.UUID;

/**
 * Gestão das sessões ativas do próprio usuário (§5.11, T-001-29).
 *
 * <p>OWN-09: uma sessão pertence exclusivamente ao seu {@code userId}. Operar sobre a sessão de
 * outro usuário devolve {@code 404}, nunca {@code 403} — o identificador alheio não deve ser
 * confirmável (ART-024).
 */
public interface SessionService {

    /**
     * @param currentSessionId sessão que originou a requisição, marcada com {@code current = true}
     */
    ActiveSessionListResponse listOwn(UUID userId, UUID currentSessionId);

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2002} quando a sessão
     *     não existe <b>ou</b> pertence a outro usuário
     */
    void revokeOwn(UUID userId, UUID sessionId);
}
