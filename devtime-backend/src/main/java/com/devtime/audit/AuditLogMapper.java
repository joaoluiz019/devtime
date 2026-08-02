package com.devtime.audit;

import com.devtime.audit.domain.ActorType;
import com.devtime.audit.domain.AuditLog;
import com.devtime.audit.dto.AuditLogResponses.AuditActorResponse;
import com.devtime.audit.dto.AuditLogResponses.AuditChangeResponse;
import com.devtime.audit.dto.AuditLogResponses.AuditLogResponse;
import com.devtime.shared.observability.IpAddressMasker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Conversão de {@link AuditLog} para a resposta de {@code users.md} §10.1.
 *
 * <p>Escrito à mão, e não por MapStruct: as três transformações que importam — recompor {@code
 * changes[]} a partir de dois JSONB, mascarar o IP e resolver o nome do autor — não são mapeamentos
 * campo a campo. BR-105 continua respeitado: o mapper não acessa banco; os resumos de usuário
 * chegam prontos, resolvidos em lote pelo serviço.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogMapper {

    /** §19.1: o IP é dado pessoal; a trilha o exibe truncado. */
    private static final String METADATA_IP = "ipAddress";

    private static final ActorType DEFAULT_ACTOR_TYPE = ActorType.SYSTEM;

    /**
     * RN-458: mesmo texto de {@code UserSummary.REMOVED_USER_NAME}.
     *
     * <p>Declarado aqui em vez de referenciado: {@code audit} não pode depender de {@code user}
     * (ciclo, BR-008), e a coerência entre as duas constantes é verificada por teste.
     */
    static final String REMOVED_ACTOR_NAME = "Usuário Removido";

    private final ObjectMapper objectMapper;

    public AuditLogResponse toResponse(AuditLog entry, Map<UUID, String> actorNames) {
        Map<String, Object> before = readMap(entry.getBeforeState());
        Map<String, Object> after = readMap(entry.getAfterState());
        return new AuditLogResponse(
                entry.getId(),
                entry.getOccurredAt(),
                toActor(entry, actorNames),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                toChanges(before, after),
                maskMetadata(readMap(entry.getMetadata())));
    }

    private AuditActorResponse toActor(AuditLog entry, Map<UUID, String> actorNames) {
        ActorType type = entry.getActorType() == null ? DEFAULT_ACTOR_TYPE : entry.getActorType();
        if (entry.getActorId() == null) {
            return new AuditActorResponse(null, null, type);
        }
        String resolved = actorNames.get(entry.getActorId());
        // RN-458: o vínculo histórico é preservado; apenas o nome exibido é substituído quando a
        // conta não existe mais. Omitir a entrada esconderia justamente quem agiu.
        String name = resolved == null ? REMOVED_ACTOR_NAME : resolved;
        return new AuditActorResponse(entry.getActorId(), name, type);
    }

    /**
     * União ordenada das chaves dos dois estados.
     *
     * <p>Percorrer apenas {@code afterState} perderia o campo apagado (presente antes, ausente
     * depois), que é exatamente a alteração que uma disputa contratual pergunta (ART-003).
     */
    private List<AuditChangeResponse> toChanges(
            Map<String, Object> before, Map<String, Object> after) {
        LinkedHashSet<String> fields = new LinkedHashSet<>(before.keySet());
        fields.addAll(after.keySet());
        List<AuditChangeResponse> changes = new ArrayList<>(fields.size());
        for (String field : fields) {
            String previous = asText(before.get(field));
            String current = asText(after.get(field));
            if (!Objects.equals(previous, current)) {
                changes.add(new AuditChangeResponse(field, previous, current));
            }
        }
        return List.copyOf(changes);
    }

    private Map<String, Object> maskMetadata(Map<String, Object> metadata) {
        if (!metadata.containsKey(METADATA_IP)) {
            return metadata;
        }
        Map<String, Object> masked = new LinkedHashMap<>(metadata);
        masked.put(METADATA_IP, IpAddressMasker.mask(asText(metadata.get(METADATA_IP))));
        return Map.copyOf(masked);
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * JSON malformado devolve mapa vazio em vez de propagar a falha.
     *
     * <p>A trilha é lida em auditoria e investigação: uma entrada corrompida não pode impedir a
     * leitura das demais. A ocorrência é registrada em log para diagnóstico.
     */
    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            log.warn("Entrada de auditoria com JSON ilegível ignorada na conversão");
            return Map.of();
        }
    }
}
