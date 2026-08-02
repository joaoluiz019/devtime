package com.devtime.tenant.domain;

import com.devtime.shared.persistence.Address;
import com.devtime.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Raiz de isolamento (entities.md §6.1).
 *
 * <p>Não estende {@code TenantScopedEntity}: ART-013 exclui {@code Tenant} do escopo de tenant —
 * ele <b>é</b> o tenant. Por isso não recebe o filtro {@code tenantFilter}.
 *
 * <p>O acesso a esta entidade a partir de outra feature é proibido (BR-002); o caminho é a
 * interface pública {@code TenantService}, criada pela feature 002.
 */
@Entity
@Table(name = "tenants")
@SQLRestriction("deleted_at IS NULL") // BR-029
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** INV-TEN-01: único globalmente entre tenants não excluídos. Imutável após a criação. */
    @Column(name = "slug", nullable = false, updatable = false, length = 60)
    private String slug;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "document_number", length = 20)
    private String documentNumber;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    /** ART-032 / INV-TEN-03: ID IANA resolvível. */
    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    /** ART-041: moeda ISO-4217 do tenant. {@code CHAR(3)} conforme database.md §4.2. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Embedded private Address address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status;

    /** Reservado para F6. */
    @Column(name = "plan_code", nullable = false, length = 30)
    private String planCode;

    /**
     * Instante do cancelamento (V030).
     *
     * <p>Não deriva de {@code updatedAt}: qualquer alteração posterior — inclusive uma exportação —
     * reiniciaria a retenção de 30 dias de RN-008.
     */
    @Column(name = "cancelled_at")
    private java.time.Instant cancelledAt;

    /** RN-008: {@code cancelledAt + 30 dias}. Serve ao {@code TenantPurgeJob}. */
    @Column(name = "purge_scheduled_at")
    private java.time.Instant purgeScheduledAt;

    /** users.md §6.3: motivo declarado pelo titular, para análise de churn. */
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /**
     * Preferências operacionais do tenant (entities.md §6.1.1).
     *
     * <p>CF-04: valores padrão de negócio ficam aqui, não em configuração global da aplicação —
     * cada tenant precisa poder divergir sem novo deploy.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false)
    private String settings;
}
