package com.devtime.tenant.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import com.devtime.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Vínculo entre {@link com.devtime.user.domain.User} e {@link Tenant}, com papel (entities.md
 * §6.3).
 *
 * <p>Primeira entidade tenant-scoped do sistema: recebe o filtro {@code tenantFilter} (ART-022) e a
 * restrição de exclusão lógica (BR-029).
 *
 * <p>As referências a {@code User} são {@code UUID}, não associações {@code @ManyToOne}: BR-002
 * proíbe que uma feature dependa da entidade de outra, e a navegação por objeto criaria exatamente
 * esse acoplamento. A integridade é garantida pelas FKs da migration V004.
 */
@Entity
@Table(name = "memberships")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Membership extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** TK-05 / IMP-04: alteração de papel invalida os access tokens do usuário neste tenant. */
    @Column(name = "role_changed_at", nullable = false)
    private Instant roleChangedAt;

    /** ART-040: dinheiro em {@code BigDecimal}, nunca {@code double}. Custo interno (F5). */
    @Column(name = "default_hourly_cost", precision = 19, scale = 4)
    private BigDecimal defaultHourlyCost;

    /**
     * ART-041: nenhuma coluna monetária sem moeda explícita.
     *
     * <p>{@code SqlTypes.CHAR} é obrigatório aqui: database.md §4.2 fixa moeda como {@code
     * CHAR(3)}, e o padrão de Hibernate para {@code String} é {@code varchar}. Sem esta declaração,
     * {@code ddl-auto=validate} recusa a inicialização por divergência de tipo (ART-054).
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "cost_currency", length = 3)
    private String costCurrency;
}
