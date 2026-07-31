package com.devtime.tag.domain;

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
 * Rótulo livre do tenant (entities.md §6.11).
 *
 * <p>Oposto deliberado da categoria: opcional, múltiplo e descartável. Responde "a que
 * <b>assunto</b> o trabalho pertence", enquanto a categoria responde "que <b>tipo</b> de trabalho
 * foi feito".
 *
 * <p>{@code Tag} não possui campo {@code status}: os estados do ciclo de vida descritos em §9.2 da
 * spec 006 (em uso, órfã, sugerida) derivam inteiramente de {@code usageCount} e {@code updatedAt}.
 * Persistir um enum para uma condição derivável duplicaria a fonte da verdade.
 */
@Entity
@Table(name = "tags")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Tag extends TenantScopedEntity {

    /** INV-TAG-03: persistido sempre em forma normalizada (RN-506). */
    @Column(name = "name", nullable = false, length = 40)
    private String name;

    /** {@code CHAR(7)} no schema; o padrão de Hibernate para {@code String} é {@code varchar}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    /**
     * 💾 INV-TAG-04: número de vínculos ativos.
     *
     * <p>Atualizado por incremento na transação do vínculo, nunca por reagregação — a listagem
     * ordenada por uso é o caminho quente da feature e uma agregação ao vivo custaria mais do que
     * resolve (§20.1 da spec).
     */
    @Column(name = "usage_count", nullable = false)
    private int usageCount;
}
