package com.devtime.category.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Categoria de trabalho do tenant (entities.md §6.10).
 *
 * <p>Classifica a natureza de cada registro de horas e determina o valor inicial de {@code
 * billable} no work log (RN-112). Todo tenant nasce com as 9 categorias de {@link
 * DefaultCategoryCatalog} (RN-501), e as de sistema não podem ser excluídas (RN-503) — o catálogo é
 * editável, mas nunca vazio (INV-CAT-02).
 */
@Entity
@Table(name = "categories")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Category extends TenantScopedEntity {

    /**
     * RN-502: único por tenant, sem diferenciar caixa. Acentos <b>são</b> diferenciados (CX-02).
     */
    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /** {@code CHAR(7)} no schema; o padrão de Hibernate para {@code String} é {@code varchar}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    /** Nome do ícone PrimeIcons. */
    @Column(name = "icon", length = 40)
    private String icon;

    @Column(name = "billable_by_default", nullable = false)
    private boolean billableByDefault;

    /** RN-504: inativar não afeta registros existentes; a categoria deixa de ser oferecida. */
    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** INV-CAT-05 / RN-503: imutável e não excluível. {@code updatable = false} é a barreira. */
    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;
}
