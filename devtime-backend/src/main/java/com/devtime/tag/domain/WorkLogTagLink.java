package com.devtime.tag.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Vínculo entre registro de horas e etiqueta (tabela {@code work_log_tags}, V028).
 *
 * <p>Simétrica a {@link TicketTagLink} e pelas mesmas razões: junção pura, sem identidade própria,
 * sem {@code version} — o vínculo existe ou não existe — e sem {@code deleted_at}, porque
 * desvincular é remover a aresta, não preservá-la excluída. A isenção de P-03 é a mesma já
 * declarada em {@code PersistenceRulesTest}.
 *
 * <p>O vínculo pertence a {@code 006-tags}, e não a {@code 008-worklogs}, porque a contagem de uso
 * da etiqueta (INV-TAG-04) precisa ser mantida no mesmo lugar para os dois alvos. Com o vínculo
 * dividido entre features, {@code usageCount} teria dois donos.
 */
@Entity
@Table(name = "work_log_tags")
@IdClass(WorkLogTagLinkId.class)
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@Getter
@Setter
public class WorkLogTagLink {

    @Id
    @Column(name = "work_log_id", nullable = false, updatable = false)
    private UUID workLogId;

    @Id
    @Column(name = "tag_id", nullable = false, updatable = false)
    private UUID tagId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
