package com.devtime.audit.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Chave primária composta de {@link AuditLog}.
 *
 * <p>Existe por exigência do particionamento por {@code occurred_at}: PostgreSQL não aceita
 * constraint única em tabela particionada sem incluir a coluna de particionamento.
 *
 * @param id identificador UUIDv7 do registro
 * @param occurredAt instante do evento, também a chave de partição
 */
public record AuditLogId(UUID id, Instant occurredAt) implements Serializable {}
