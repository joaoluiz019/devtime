package com.devtime.auth;

import com.devtime.auth.dto.AuthResponses.ActiveSessionListResponse;
import com.devtime.auth.dto.AuthResponses.ActiveSessionResponse;
import com.devtime.shared.observability.IpAddressMasker;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link SessionService}.
 *
 * <p>{@code @PreAuthorize("isAuthenticated()")} e não uma permissão de papel: gerir as próprias
 * sessões é direito de qualquer usuário autenticado, inclusive {@code VIEWER} (§16 do spec). O
 * recorte de segurança aqui é de <b>ownership</b>, aplicado no repositório.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionServiceImpl implements SessionService {

    private final RefreshTokenService refreshTokenService;
    private final AuthAuditRecorder auditRecorder;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ActiveSessionListResponse listOwn(UUID userId, UUID currentSessionId) {
        return new ActiveSessionListResponse(
                refreshTokenService.listActive(userId).stream()
                        .map(
                                session ->
                                        new ActiveSessionResponse(
                                                session.id(),
                                                Objects.equals(session.id(), currentSessionId),
                                                session.userAgent(),
                                                IpAddressMasker.mask(session.ipAddress()),
                                                session.createdAt(),
                                                session.lastUsedAt(),
                                                session.expiresAt()))
                        .toList());
    }

    @Override
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void revokeOwn(UUID userId, UUID sessionId) {
        refreshTokenService.revokeOwned(userId, sessionId); // OWN-09
        auditRecorder.record(
                "SESSION_REVOKED",
                AuthAuditRecorder.ENTITY_SESSION,
                sessionId,
                Map.of(),
                Map.of("revokedBy", userId.toString()));
    }
}
