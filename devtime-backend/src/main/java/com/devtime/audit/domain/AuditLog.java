package com.devtime.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Trilha de auditoria <i>append-only</i> (entities.md §6.20, database.md §7.11).
 *
 * <p><b>Não estende {@code BaseEntity}</b>, ao contrário de BR-020. A exceção é declarada em
 * database.md §4.3 e decorre de INV-AUD-01: a tabela não possui {@code updated_*}, {@code
 * deleted_*} nem {@code version}, porque um registro de auditoria alterável não tem valor
 * probatório (ameaça "Repudiation" de security.md §4.3). Herdar {@code BaseEntity} exigiria colunas
 * que a tabela deliberadamente não tem. A regra ArchUnit que verifica BR-020 isenta esta classe
 * explicitamente.
 *
 * <p>A chave primária é composta por {@code (id, occurredAt)} porque PostgreSQL exige que a coluna
 * de particionamento participe de toda constraint única em tabela particionada.
 */
@Entity
@Table(name = "audit_logs")
@IdClass(AuditLogId.class)
@Getter
@Setter
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Id
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Nulo em ação de sistema (CE-S-06). */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 15)
    private ActorType actorType;

    /** Ex.: {@code WORK_LOG_UPDATED}. */
    @Column(name = "action", nullable = false, updatable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    /** Somente os campos alterados, para manter o volume proporcional à mudança. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", updatable = false)
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", updatable = false)
    private String afterState;

    /**
     * Contexto mínimo de security.md §10.2: {@code traceId}, {@code ipAddress}, {@code userAgent},
     * {@code result}. Dado sensível é <b>proibido</b> aqui (ART-084).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
