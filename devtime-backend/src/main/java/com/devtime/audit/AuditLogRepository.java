package com.devtime.audit;

import com.devtime.audit.domain.AuditLog;
import com.devtime.audit.domain.AuditLogId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência da trilha de auditoria (entities.md §6.20).
 *
 * <p>Estende {@link JpaRepository} e não {@code SoftDeleteRepository}: INV-AUD-01 torna a tabela
 * <i>append-only</i>, sem {@code deleted_at} para filtrar. As consultas são restritas ao tenant
 * explicitamente porque {@link AuditLog} não estende {@code TenantScopedEntity} — não há filtro
 * automático de Hibernate a aplicar, e o {@code tenantId} vem do {@code TenantContext}, jamais da
 * requisição (BR-041).
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, AuditLogId> {

    @Query(
            """
            SELECT a FROM AuditLog a
             WHERE a.tenantId = :tenantId
               AND a.entityType = :entityType
               AND a.entityId = :entityId
             ORDER BY a.occurredAt DESC
            """)
    List<AuditLog> findByEntity(
            @Param("tenantId") UUID tenantId,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId);
}
