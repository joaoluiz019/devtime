package com.devtime.worklog.domain;

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
 * Registro atômico de tempo trabalhado (entities.md §6.13).
 *
 * <p>É o dado que o cliente compra, o número que aparece na fatura e a linha do relatório que
 * sustenta uma conversa contratual. Todo o resto do DevTime existe para produzir, proteger ou
 * explicar work logs.
 *
 * <p><b>Não possui campo {@code status} nem máquina de estados.</b> Um work log não transiciona:
 * ele é criado, possivelmente editado e possivelmente excluído. Os três estados observáveis —
 * editável, travado e excluído — derivam de {@code lockedAt} e {@code deletedAt}, e {@code
 * lockedAt} é imposto <b>de fora</b>, pelo fechamento do período em {@code 011-bank-hours}. Modelar
 * isso como estado próprio criaria uma máquina cujas transições pertencem a outra entidade.
 *
 * <p>As referências a {@code Ticket}, {@code Contract}, {@code Client}, {@code ContractPeriod},
 * {@code User}, {@code Category} e {@code Timer} são {@code UUID}, nunca associações: BR-002 proíbe
 * depender da entidade de outra feature. A integridade vem das FKs de V016.
 */
@Entity
@Table(name = "work_logs")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class WorkLog extends TenantScopedEntity {

    /** RN-101: não existe registro de horas avulso. */
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    /**
     * 🔒💾 RN-109 / INV-WKL-06: copiado do ticket na criação e nunca alterado.
     *
     * <p>Desnormalizado por integridade histórica, não por desempenho: mover o ticket de contrato
     * hoje não pode mudar a que contrato as horas de março pertenceram (ART-005).
     */
    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    /** 🔒💾 RN-109: copiado do contrato na criação. */
    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    /** 💾 RN-107 / INV-WKL-08: período cujo intervalo fechado contém {@code workDate}. */
    @Column(name = "contract_period_id", nullable = false)
    private UUID contractPeriodId;

    /**
     * 🔒 OWN-01: o registro pertence a quem <b>trabalhou</b>, não a quem o lançou.
     *
     * <p>Um {@code MANAGER} que lança em nome de outro membro (RN-106) não se torna dono: o membro
     * é, e por isso pode editar o próprio registro criado por terceiro.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** RN-104: categoria ativa do tenant, pré-selecionada pela cadeia da §6.2 de {@code 005}. */
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** RN-108 / ART-031: data local de {@code startedAt} no fuso do tenant. */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** ART-030: instante em UTC. */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** RN-114 / INV-WKL-01: estritamente maior que {@code startedAt}. */
    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    /** 💾 RN-110: sempre calculado; ausente de todo DTO de escrita (SG-09). */
    @Column(name = "gross_minutes", nullable = false)
    private int grossMinutes;

    /** RN-116 / INV-WKL-04: {@code 0 ≤ pausedMinutes < grossMinutes}. */
    @Column(name = "paused_minutes", nullable = false)
    private int pausedMinutes;

    /** 💾 RN-111 + RN-113: {@code gross − paused}, arredondado <b>para baixo</b>. */
    @Column(name = "net_minutes", nullable = false)
    private int netMinutes;

    /** RN-105: 3 a 2.000 caracteres. É o que o cliente lê no relatório. */
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** RN-112 / RN-223: apenas horas faturáveis consomem saldo. */
    @Column(name = "billable", nullable = false)
    private boolean billable;

    /** 🔒 RN-126. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private WorkLogSource source;

    /** 🔒 RN-126 / INV-WKL-09: não nulo quando {@code source = TIMER}. */
    @Column(name = "timer_id", updatable = false)
    private UUID timerId;

    /** RN-121 / INV-WKL-07: preenchido pelo fechamento do período; enquanto não nulo, imutável. */
    @Column(name = "locked_at")
    private Instant lockedAt;

    /**
     * RN-123: contra-métrica de qualidade de captura.
     *
     * <p>Índice alto não indica flexibilidade desejada — indica formulário ruim ou regra confusa.
     */
    @Column(name = "edit_count", nullable = false)
    private int editCount;

    /** RN-112: campo derivado; só horas faturáveis consomem o saldo do contrato. */
    public int billableMinutes() {
        return billable ? netMinutes : 0;
    }

    /** RN-121: registro de período fechado é somente leitura até a reabertura formal. */
    public boolean isLocked() {
        return lockedAt != null;
    }
}
