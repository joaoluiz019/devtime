package com.devtime.category.domain;

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
 * Leitura histórica de {@code categories}, <b>inclusive excluídas logicamente</b> (spec 005 §22,
 * OB-04).
 *
 * <p>Existe como entidade separada de {@link Category} por uma limitação deliberada de Hibernate:
 * {@code @SQLRestriction("deleted_at IS NULL")} é estático e não pode ser desligado por consulta. A
 * alternativa seria SQL nativo, que escaparia <b>também</b> do {@code @Filter} de tenant e exigiria
 * escrever {@code tenant_id = ?} à mão — exatamente o que BR-046 proíbe e o modo de falha que
 * ART-022 existe para eliminar. Um segundo mapeamento sobre a mesma tabela abre mão de uma
 * restrição e preserva a outra.
 *
 * <p>{@code @Immutable}: esta é a única entidade do domínio que enxerga registro excluído, e nada
 * pode ser escrito por ela. A escrita continua sendo exclusividade de {@link Category}, onde a
 * restrição de exclusão lógica vale.
 *
 * <p>Isenta de BR-029 na suíte ArchUnit, com a mesma explicitação de {@code AuditLog}: a ausência
 * da anotação é a razão de ser da classe, não um esquecimento.
 */
@Entity
@Table(name = "categories")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@Immutable
@Getter
public class CategoryHistory extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "icon", length = 40)
    private String icon;

    @Column(name = "billable_by_default", nullable = false)
    private boolean billableByDefault;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_system", nullable = false)
    private boolean system;
}
