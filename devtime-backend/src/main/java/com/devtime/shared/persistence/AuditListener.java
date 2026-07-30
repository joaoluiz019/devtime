package com.devtime.shared.persistence;

import com.devtime.shared.tenancy.CrossTenantWriteException;
import com.devtime.shared.tenancy.TenantContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Preenche identidade, auditoria e tenant em toda entidade de domínio.
 *
 * <p>É a camada 3 da defesa em profundidade de {@code security.md} §6.1: o {@code tenant_id} de
 * escrita vem do {@link TenantContext}, nunca da requisição (ART-021, BR-041), e a tentativa de
 * gravar em outro tenant é rejeitada.
 *
 * <p>BR-140/BR-141: o instante nunca vem de {@code Instant.now()}; vem sempre do {@link Clock}
 * injetado, o que torna o comportamento determinístico em teste (BR-205).
 */
@Component
@RequiredArgsConstructor
public class AuditListener {

    private final TenantContext tenantContext;
    private final Clock clock;

    @PrePersist
    void onPrePersist(BaseEntity entity) {
        if (entity.getId() == null) {
            entity.setId(UuidGenerator.newId()); // ART-010: UUIDv7 gerado na aplicação
        }
        Instant now = clock.instant();
        UUID actor = tenantContext.currentUserId().orElse(null); // nulo em criação de sistema

        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        if (entity.getVersion() == null) {
            entity.setVersion(0L);
        }
        if (entity instanceof TenantScopedEntity scoped) {
            assignTenant(scoped);
        }
    }

    @PreUpdate
    void onPreUpdate(BaseEntity entity) {
        entity.setUpdatedAt(clock.instant());
        tenantContext.currentUserId().ifPresent(entity::setUpdatedBy);
    }

    /**
     * Atribui o tenant da sessão, ou valida o já atribuído.
     *
     * <p>Um {@code tenantId} pré-atribuído divergente do contexto é rejeitado em vez de
     * sobrescrito: sobrescrever silenciosamente moveria o registro entre tenants, corrompendo dados
     * de ambos (último cenário de {@code security.md} §6.3).
     */
    private void assignTenant(TenantScopedEntity entity) {
        UUID contextTenantId = tenantContext.requireTenantId();
        UUID entityTenantId = entity.getTenantId();
        if (entityTenantId == null) {
            entity.setTenantId(contextTenantId);
            return;
        }
        if (!entityTenantId.equals(contextTenantId)) {
            throw new CrossTenantWriteException(entity.getClass().getSimpleName());
        }
    }
}
