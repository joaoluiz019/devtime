package com.devtime.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Superclasse das entidades pertencentes a um tenant (ART-013, BR-040).
 *
 * <p>O {@code tenantId} é imutável após a criação ({@code updatable = false}) e nunca é atribuído a
 * partir da requisição HTTP (ART-021, BR-041): quem o preenche é o {@link AuditListener}, a partir
 * do {@link com.devtime.shared.tenancy.TenantContext} — camada 3 da defesa em profundidade descrita
 * em {@code security.md} §6.1.
 *
 * <p>Cada entidade concreta deve declarar {@code @Filter(name = TENANT_FILTER, condition =
 * TENANT_CONDITION)} e {@code @SQLRestriction("deleted_at IS NULL")}: em Hibernate, essas anotações
 * não são herdadas de uma {@code @MappedSuperclass}. As regras ArchUnit BR-029/BR-040 verificam a
 * presença em todas elas.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class TenantScopedEntity extends BaseEntity {

    public static final String TENANT_FILTER = "tenantFilter";
    public static final String TENANT_PARAM = "tenantId";
    public static final String TENANT_CONDITION = "tenant_id = :tenantId";

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
}
