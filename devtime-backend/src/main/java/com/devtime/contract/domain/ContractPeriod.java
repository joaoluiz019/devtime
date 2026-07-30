package com.devtime.contract.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Ciclo de apuração do contrato (entities.md §6.7).
 *
 * <p><b>Fronteira com {@code 011-bank-hours}:</b> esta feature cria e mantém {@code
 * contractedMinutes}, {@code startDate}, {@code endDate}, {@code sequence} e {@code status} até
 * {@code OPEN}. A partir daí, {@code consumedMinutes}, {@code carriedIn/Out}, {@code
 * adjustmentMinutes} e as transições de fechamento pertencem a {@code 011}. {@code 004}
 * <b>nunca</b> calcula saldo.
 *
 * <p>Os campos de saldo existem aqui porque são colunas da mesma tabela; permanecem nos seus
 * defaults enquanto {@code 011} não os alimenta.
 */
@Entity
@Table(name = "contract_periods")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class ContractPeriod extends TenantScopedEntity {

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    /** 🔒 INV-PER-01: 1, 2, 3… por contrato. */
    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Column(name = "label", nullable = false, length = 30)
    private String label;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** <b>Inclusive</b> (entities.md §7.2). Alterável apenas por truncamento (RN-214). */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private PeriodStatus status;

    /** 🔒 Congelado na criação: alterar {@code monthlyMinutes} não reescreve o passado (RN-207). */
    @Column(name = "contracted_minutes", nullable = false)
    private int contractedMinutes;

    /** Alimentado pelo fechamento do período anterior — {@code 011}. */
    @Column(name = "carried_in_minutes", nullable = false)
    private int carriedInMinutes;

    @Column(name = "carried_out_minutes", nullable = false)
    private int carriedOutMinutes;

    @Column(name = "adjustment_minutes", nullable = false)
    private int adjustmentMinutes;

    @Column(name = "consumed_minutes", nullable = false)
    private int consumedMinutes;

    @Column(name = "non_billable_minutes", nullable = false)
    private int nonBillableMinutes;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reopen_count", nullable = false)
    private short reopenCount;

    /** 🔒 Congelado do contrato: o valor do período não muda se a tabela de preços mudar. */
    @Column(name = "hourly_rate_snapshot", precision = 19, scale = 4)
    private BigDecimal hourlyRateSnapshot;

    @Column(name = "overage_rate_snapshot", precision = 19, scale = 4)
    private BigDecimal overageRateSnapshot;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
}
