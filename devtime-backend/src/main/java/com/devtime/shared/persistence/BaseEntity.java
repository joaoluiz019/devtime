package com.devtime.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ParamDef;

/**
 * Superclasse de toda entidade de domínio (ART-050, BR-020).
 *
 * <p>Reúne identidade, auditoria, exclusão lógica e controle de concorrência otimista. A coluna
 * {@code tenant_id} <b>não</b> está aqui: ART-013 exclui {@code Tenant} e {@code User} do escopo de
 * tenant, e um {@code @Filter} herdado por essas entidades geraria SQL referenciando uma coluna
 * inexistente. As entidades tenant-scoped estendem {@link TenantScopedEntity}, que por sua vez
 * estende esta classe — mantendo ART-050 satisfeito para todas.
 *
 * <p>O {@code @FilterDef} é declarado aqui porque em Hibernate a definição do filtro é global,
 * enquanto sua aplicação ({@code @Filter}) precisa estar em cada entidade concreta. Pelo mesmo
 * motivo, {@code @SQLRestriction("deleted_at IS NULL")} (BR-029) é declarado em cada entidade e não
 * nesta superclasse: anotações de restrição em {@code @MappedSuperclass} não são herdadas.
 */
@MappedSuperclass
@EntityListeners(AuditListener.class)
@org.hibernate.annotations.FilterDef(
        name = TenantScopedEntity.TENANT_FILTER,
        parameters = @ParamDef(name = TenantScopedEntity.TENANT_PARAM, type = UUID.class))
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /** ART-051: a exclusão preenche este campo; {@code DELETE} físico é proibido. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    /** ART-052: conflito de versão retorna 409 Conflict. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * BR-033: a identidade é o {@code id}, nunca campos mutáveis.
     *
     * <p>Como o {@code id} é atribuído antes da persistência (UUIDv7 gerado na aplicação, ART-010),
     * ele já está disponível para instâncias transientes — diferente de identificadores gerados
     * pelo banco, em que duas entidades novas seriam indistinguíveis.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }
}
