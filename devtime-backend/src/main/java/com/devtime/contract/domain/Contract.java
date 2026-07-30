package com.devtime.contract.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Acordo comercial com um cliente (entities.md §6.6).
 *
 * <p>Define <b>quantas horas existem</b> e <b>em qual janela</b>. É o objeto central do produto:
 * sem períodos contíguos não há onde alocar uma hora trabalhada (RN-107) e o banco de horas não
 * existe.
 *
 * <p>As referências a {@code Client} e {@code Category} são {@code UUID}, não associações: BR-002
 * proíbe depender da entidade de outra feature. A integridade vem das FKs de V012.
 */
@Entity
@Table(name = "contracts")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Contract extends TenantScopedEntity {

    /** 🔒 RN-201: o cliente precisa estar {@code ACTIVE} na criação. */
    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    /** INV-CTR-01: sequencial {@code CT-0001}, único por tenant. */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** 🔒 fora de {@code DRAFT} (RN-206). */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ContractType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private ContractStatus status;

    /** RN-202: obrigatório em {@code MONTHLY_HOURS}; entre 1 e 44.640. */
    @Column(name = "monthly_minutes")
    private Integer monthlyMinutes;

    /** ART-031: data de calendário no fuso do tenant. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** RN-203: 1 a 28. Dias 29–31 não existem em todos os meses. */
    @Column(name = "billing_day", nullable = false)
    private short billingDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "rollover_policy", nullable = false, length = 10)
    private RolloverPolicy rolloverPolicy;

    @Column(name = "rollover_cap_minutes")
    private Integer rolloverCapMinutes;

    /** RN-230: número de períodos até o saldo transportado expirar; {@code 0} nunca expira. */
    @Column(name = "rollover_expiry_periods", nullable = false)
    private short rolloverExpiryPeriods;

    @Enumerated(EnumType.STRING)
    @Column(name = "overage_policy", nullable = false, length = 20)
    private OveragePolicy overagePolicy;

    /** ART-040: dinheiro em {@code BigDecimal}. Valor informativo por hora. */
    @Column(name = "hourly_rate", precision = 19, scale = 4)
    private BigDecimal hourlyRate;

    @Column(name = "overage_rate", precision = 19, scale = 4)
    private BigDecimal overageRate;

    /** ART-041: nenhuma coluna monetária sem moeda explícita. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    /** RN-217: rateio do primeiro período. Decisão explícita do usuário (CX-07). */
    @Column(name = "prorate_first_period", nullable = false)
    private boolean prorateFirstPeriod;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "notification_thresholds", nullable = false)
    private Short[] notificationThresholds;

    /** Pré-seleção de categoria no registro de horas (RN-104, cadeia da §6.2 de {@code 005}). */
    @Column(name = "default_category_id")
    private UUID defaultCategoryId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
