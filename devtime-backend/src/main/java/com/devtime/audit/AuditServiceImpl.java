package com.devtime.audit;

import com.devtime.audit.domain.ActorType;
import com.devtime.audit.domain.AuditLog;
import com.devtime.audit.dto.AuditEntry;
import com.devtime.shared.observability.TraceContext;
import com.devtime.shared.persistence.UuidGenerator;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrita da trilha de auditoria (RN-006, entities.md §6.20).
 *
 * <p>Participa da transação corrente ({@code Propagation.REQUIRED}, o padrão) por exigência
 * explícita de RN-006: se a alteração for revertida, o registro de auditoria também precisa ser —
 * caso contrário a trilha passaria a afirmar mudanças que não ocorreram.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository repository;
    private final TenantContext tenantContext;
    private final TenantClock clock;
    private final ObjectMapper objectMapper;

    @Override
    public void record(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState) {
        record(action, entityType, entityId, beforeState, afterState, Map.of());
    }

    @Override
    public void record(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Map<String, Object> extraMetadata) {
        UUID actorId = tenantContext.currentUserId().orElse(null);
        ActorType actorType = actorId == null ? ActorType.SYSTEM : ActorType.USER;
        persist(
                action,
                entityType,
                entityId,
                beforeState,
                afterState,
                actorId,
                actorType,
                extraMetadata);
    }

    @Override
    public void recordSystemAction(
            String action, String entityType, UUID entityId, Map<String, Object> afterState) {
        recordSystemAction(action, entityType, entityId, Map.of(), afterState, Map.of());
    }

    @Override
    public void recordSystemAction(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Map<String, Object> extraMetadata) {
        persist(
                action,
                entityType,
                entityId,
                beforeState,
                afterState,
                null,
                ActorType.SYSTEM,
                extraMetadata);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<AuditEntry> findByEntity(String entityType, UUID entityId) {
        return repository
                .findByEntity(tenantContext.requireTenantId(), entityType, entityId)
                .stream()
                .map(this::toEntry)
                .toList();
    }

    private AuditEntry toEntry(AuditLog entry) {
        return new AuditEntry(
                entry.getId(),
                entry.getOccurredAt(),
                entry.getActorId(),
                entry.getActorType(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                fromJson(entry.getBeforeState()),
                fromJson(entry.getAfterState()),
                fromJson(entry.getMetadata()));
    }

    /**
     * Desserializa um estado da trilha.
     *
     * <p>ER-08: exibir a linha do tempo é operação não essencial diante de um registro corrompido —
     * degradar para um evento sem detalhe é melhor que derrubar a página inteira do ticket. O
     * conteúdo do JSON não entra em log (ART-084); apenas o seu tamanho.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException unreadable) {
            log.warn("estado de auditoria ilegível tamanho={}", json.length());
            return Map.of();
        }
    }

    private void persist(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            UUID actorId,
            ActorType actorType,
            Map<String, Object> extraMetadata) {
        AuditLog entry = new AuditLog();
        entry.setId(UuidGenerator.newId());
        entry.setOccurredAt(clock.now());
        entry.setTenantId(tenantContext.requireTenantId()); // BR-042
        entry.setActorId(actorId);
        entry.setActorType(actorType);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBeforeState(toJson(beforeState));
        entry.setAfterState(toJson(afterState));
        entry.setMetadata(toJson(metadata(extraMetadata)));
        entry.setCreatedAt(clock.now());
        entry.setCreatedBy(actorId);
        repository.save(entry);
    }

    /** security.md §10.2: contexto mínimo. Nenhum dado sensível é registrado aqui (ART-084). */
    private Map<String, Object> metadata(Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("traceId", TraceContext.currentTraceId());
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }
        return metadata;
    }

    private String toJson(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException failure) {
            // ER-08: a auditoria é essencial; falhar aqui deve propagar e reverter a operação,
            // porque uma alteração sem trilha é exatamente o que RN-006 existe para impedir.
            throw new AuditSerializationException(failure);
        }
    }
}
