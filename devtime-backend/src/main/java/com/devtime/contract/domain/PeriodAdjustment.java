package com.devtime.contract.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Ajuste manual e auditável do saldo de um período (entities.md §6.8).
 *
 * <p><b>INV-ADJ-01: o ajuste é imutável.</b> Todos os campos são {@code updatable = false} e não
 * existe rota de {@code PATCH} nem de {@code DELETE} (RN-236). Correção se faz por um novo ajuste
 * de sinal contrário — o estorno fica no extrato que o cliente vê, e é exatamente esse o ponto: um
 * ajuste que pudesse ser editado depois não explicaria nada sobre o saldo que ele produziu.
 */
@Entity
@Table(name = "period_adjustments")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class PeriodAdjustment extends TenantScopedEntity {

    /** 🔒 RN-235: o período precisa estar {@code OPEN} ou {@code REOPENED} na aplicação. */
    @Column(name = "contract_period_id", nullable = false, updatable = false)
    private UUID contractPeriodId;

    /** 🔒 positivo credita, negativo debita; nunca zero. */
    @Column(name = "minutes", nullable = false, updatable = false)
    private int minutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 20)
    private AdjustmentReason reason;

    /** 🔒 RN-215: mínimo de 10 caracteres. */
    @Column(name = "justification", nullable = false, updatable = false, columnDefinition = "text")
    private String justification;

    /** 🔒 sempre o usuário autenticado, ou nulo em ajuste automático de sistema (RN-230). */
    @Column(name = "applied_by", nullable = false, updatable = false)
    private UUID appliedBy;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;
}
