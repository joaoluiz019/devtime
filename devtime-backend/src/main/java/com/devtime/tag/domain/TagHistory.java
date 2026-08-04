package com.devtime.tag.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Leitura histórica de {@code tags}, <b>inclusive excluídas logicamente</b> (spec 006 §22).
 *
 * <p>Mesma decisão e mesmo motivo de {@link com.devtime.category.domain.CategoryHistory}: um
 * segundo mapeamento sobre a mesma tabela abre mão do corte de exclusão lógica e preserva o filtro
 * de tenant, que SQL nativo perderia. {@code @Immutable} porque toda escrita continua sendo de
 * {@link Tag}.
 */
@Entity
@Table(name = "tags")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@Immutable
@Getter
public class TagHistory extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;
}
