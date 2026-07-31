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
 * Vínculo entre ticket e etiqueta (entities.md §6.11, tabela {@code ticket_tags}).
 *
 * <p><b>Não estende {@code BaseEntity}, por decisão registrada:</b> é uma tabela de junção pura,
 * sem identidade própria e sem ciclo de vida. Não possui {@code version} (nada nela é editável: o
 * vínculo existe ou não existe), nem {@code deleted_at} — §9.3 da spec 006 determina explicitamente
 * a <b>remoção física</b> das linhas de vínculo ao excluir a tag. P-03 proíbe {@code DELETE} em
 * entidade de <b>domínio</b>; uma linha de junção não é dado de negócio, é a aresta entre dois. A
 * isenção é declarada em {@code PersistenceRulesTest}, ao lado da de {@code AuditLog}.
 *
 * <p>O {@code @Filter} de tenant é mantido: sem ele, a contagem de uso de uma tag misturaria
 * vínculos de outros tenants (ART-022).
 *
 * <p>A chave composta {@code (ticketId, tagId)} é o que torna o vínculo idempotente (CX-10): pedir
 * o mesmo vínculo duas vezes não cria segunda linha nem incrementa {@code usageCount}.
 */
@Entity
@Table(name = "ticket_tags")
@IdClass(TicketTagLinkId.class)
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@Getter
@Setter
public class TicketTagLink {

    @Id
    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

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
