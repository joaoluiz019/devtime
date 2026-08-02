package com.devtime.audit.dto;

import com.devtime.audit.domain.ActorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Saídas da consulta à trilha de auditoria (users.md §10.1). */
public final class AuditLogResponses {

    private AuditLogResponses() {}

    /**
     * Autor da alteração.
     *
     * @param id nulo em ação de sistema (CE-S-06)
     * @param name nome de exibição resolvido; {@code Usuário Removido} quando a conta não existe
     *     mais (RN-458)
     */
    @Schema(name = "AuditActor")
    public record AuditActorResponse(UUID id, String name, ActorType type) {}

    /**
     * Diferença de um campo entre o estado anterior e o posterior.
     *
     * <p>A API expõe {@code changes[]} e não os dois mapas crus de {@code entities.md} §6.20: a
     * tela precisa listar "o que mudou", e reconstruir isso no cliente exigiria que cada consumidor
     * reimplementasse a comparação — com resultados divergentes para campos presentes em apenas um
     * dos lados.
     *
     * @param before valor anterior serializado; nulo em criação
     * @param after valor posterior serializado; nulo em exclusão
     */
    @Schema(name = "AuditChange")
    public record AuditChangeResponse(String field, String before, String after) {}

    /**
     * Entrada da trilha.
     *
     * @param metadata contexto de {@code security.md} §10.2 com o IP <b>mascarado</b> (§19.1); o
     *     endereço em claro nunca sai do banco
     */
    @Schema(name = "AuditLogResponse")
    public record AuditLogResponse(
            UUID id,
            Instant occurredAt,
            AuditActorResponse actor,
            String action,
            String entityType,
            UUID entityId,
            List<AuditChangeResponse> changes,
            Map<String, Object> metadata) {}
}
