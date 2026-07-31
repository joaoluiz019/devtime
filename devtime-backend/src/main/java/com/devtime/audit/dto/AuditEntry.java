package com.devtime.audit.dto;

import com.devtime.audit.domain.ActorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Registro da trilha de auditoria exposto a outras features (entities.md §6.20).
 *
 * <p>Existe porque a linha do tempo do ticket (§9.1 de {@code tickets.md}) precisa ler a trilha, e
 * AR-02 proíbe que {@code ticket} alcance {@code AuditLog} ou {@code AuditLogRepository}. É {@code
 * record} imutável, como todo DTO (BR-100).
 *
 * @param actorId autor; nulo em ação de sistema
 * @param beforeState apenas os campos alterados, já desserializados
 * @param afterState idem
 */
@Schema(name = "AuditEntry")
public record AuditEntry(
        UUID id,
        Instant occurredAt,
        UUID actorId,
        ActorType actorType,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        Map<String, Object> metadata) {}
