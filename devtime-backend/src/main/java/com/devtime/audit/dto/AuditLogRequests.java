package com.devtime.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Entradas da consulta à trilha de auditoria (users.md §10.1). */
public final class AuditLogRequests {

    private AuditLogRequests() {}

    /**
     * Filtro da listagem (spec 002 §23, {@code AuditLogFilter}).
     *
     * <p>Todos os campos são opcionais. Quando o intervalo não é informado, o serviço aplica os
     * últimos 30 dias (CP-09, CA-12): uma consulta sem recorte varreria todas as partições mensais,
     * e a tabela cresce de 5 a 10× mais rápido que {@code work_logs} (§20.1).
     *
     * @param entityType tipo da entidade auditada, ex.: {@code MEMBERSHIP}
     * @param entityId identificador da entidade, para o histórico de um registro
     * @param actorId autor da alteração
     * @param action ação registrada, ex.: {@code MEMBERSHIP_ROLE_CHANGED}
     * @param occurredFrom início do intervalo, inclusivo
     * @param occurredTo fim do intervalo, exclusivo
     */
    @Schema(name = "AuditLogFilter")
    public record AuditLogFilter(
            String entityType,
            UUID entityId,
            UUID actorId,
            String action,
            Instant occurredFrom,
            Instant occurredTo) {

        public static AuditLogFilter empty() {
            return new AuditLogFilter(null, null, null, null, null, null);
        }
    }
}
