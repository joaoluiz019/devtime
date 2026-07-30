package com.devtime.audit;

import com.devtime.audit.domain.ActorType;
import com.devtime.audit.domain.AuditLog;
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
        UUID actorId = tenantContext.currentUserId().orElse(null);
        ActorType actorType = actorId == null ? ActorType.SYSTEM : ActorType.USER;
        persist(action, entityType, entityId, beforeState, afterState, actorId, actorType);
    }

    @Override
    public void recordSystemAction(
            String action, String entityType, UUID entityId, Map<String, Object> afterState) {
        persist(action, entityType, entityId, Map.of(), afterState, null, ActorType.SYSTEM);
    }

    private void persist(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            UUID actorId,
            ActorType actorType) {
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
        entry.setMetadata(toJson(metadata()));
        entry.setCreatedAt(clock.now());
        entry.setCreatedBy(actorId);
        repository.save(entry);
    }

    /** security.md §10.2: contexto mínimo. Nenhum dado sensível é registrado aqui (ART-084). */
    private Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("traceId", TraceContext.currentTraceId());
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
