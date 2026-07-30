package com.devtime.auth.domain;

import com.devtime.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Refresh token opaco, persistido apenas como hash (entities.md §6.19).
 *
 * <p>Não estende {@code TenantScopedEntity}: {@code tenantId} é anulável por decisão de database.md
 * §7.12 — a renovação pode preceder a seleção de tenant. Consequentemente o filtro {@code
 * tenantFilter} não se aplica, e o acesso por {@code tokenHash} é um dos usos exaustivos de
 * {@code @CrossTenant} previstos em backend.md §7.4.
 *
 * <p>RT-02: apenas o SHA-256 do token existe aqui. O valor bruto é entregue ao cliente uma única
 * vez e nunca é armazenado — um vazamento do banco não permite assumir sessões.
 */
@Entity
@Table(name = "refresh_tokens")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class RefreshToken extends BaseEntity {

    /** Tenant da sessão. Nulo no token emitido antes da seleção de tenant. */
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Elo seguinte da cadeia de rotação (RT-03).
     *
     * <p>É o que viabiliza RT-04: um token com {@code replacedById} preenchido que volta a ser
     * usado indica reuso, e a resposta segura é revogar toda a cadeia (RN-005).
     */
    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    /** AU-07: registrado para investigação de incidentes; mascarado em log (security.md §9.2). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isRotated() {
        return replacedById != null;
    }
}
