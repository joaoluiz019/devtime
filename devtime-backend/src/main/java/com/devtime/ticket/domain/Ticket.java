package com.devtime.ticket.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Unidade de trabalho (entities.md §6.12).
 *
 * <p>Todo work log pertence a um ticket (RN-101): é o que permite responder "no que essas 40 horas
 * foram gastas" com uma lista de itens nomeados em vez de descrições soltas.
 *
 * <p><b>Não existe campo {@code key}.</b> entities.md §6.12 marca a chave como 📐 (campo derivado)
 * e database.md §7.7 não declara a coluna; ela é remontada como {@code {contract.code}-{number}}
 * por {@code TicketKeyBuilder}. {@code number} é imutável ({@code updatable = false}), então a
 * chave é estável para sempre — inclusive ao mover o ticket de contrato (RN-011, CP-06).
 *
 * <p>As referências a {@code Contract}, {@code User}, {@code Category} e {@code Tag} são {@code
 * UUID}, nunca associações: BR-002 proíbe depender da entidade de outra feature. A integridade vem
 * das FKs de V014.
 */
@Entity
@Table(name = "tickets")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Ticket extends TenantScopedEntity {

    /** RN-305: só muda por endpoint dedicado, sem work logs e dentro do mesmo cliente. */
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    /** 🔒 RN-302 / INV-TCK-01: sequencial por contrato, obtido atomicamente. */
    @Column(name = "number", nullable = false, updatable = false)
    private int number;

    /** RN-303: entre 3 e 200 caracteres. */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Markdown, até 20.000 caracteres. Sanitizado na renderização, nunca na persistência. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TicketType type;

    /** ME-05: alterado exclusivamente por {@code TicketTransitionService}, nunca por PATCH. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private TicketPriority priority;

    /** RN-304: quando informado, precisa ser membership {@code ACTIVE} do tenant. */
    @Column(name = "assignee_id")
    private UUID assigneeId;

    /** 🔒 RN-011: sempre o usuário autenticado na criação; ausente de todo DTO de escrita. */
    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    /** RN-309: referência, nunca limite. Estouro sinaliza e não bloqueia. */
    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    /**
     * 💾 RN-308: soma dos {@code netMinutes}, atualizada por incremento. ART-034: minutos inteiros.
     */
    @Column(name = "spent_minutes", nullable = false)
    private int spentMinutes;

    /** 💾 INV-TCK-05: {@code spentMinutes ≥ billableMinutes ≥ 0}. */
    @Column(name = "billable_minutes", nullable = false)
    private int billableMinutes;

    /** Obrigatório ao entrar em {@code BLOCKED}; limpo ao desbloquear (§4.7). */
    @Column(name = "block_reason", length = 500)
    private String blockReason;

    /** ART-031: data de calendário no fuso do tenant. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** RN-310: preenchido na <b>primeira</b> entrada em {@code IN_PROGRESS}; nunca sobrescrito. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** RN-310 / INV-TCK-04: preenchido em {@code DONE}, limpo em toda saída. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Referência a Jira/GitHub. Persistido e sem uso no MVP; a integração é F8. */
    @Column(name = "external_ref", length = 200)
    private String externalRef;

    /** Pré-seleção de categoria do work log (RN-104, 1º elo da cadeia). */
    @Column(name = "default_category_id")
    private UUID defaultCategoryId;

    /** RN-309: nulo sem estimativa — sem referência não há estouro a sinalizar (CX-09). */
    public Boolean isOverEstimate() {
        if (estimatedMinutes == null) {
            return null;
        }
        return spentMinutes > estimatedMinutes;
    }
}
