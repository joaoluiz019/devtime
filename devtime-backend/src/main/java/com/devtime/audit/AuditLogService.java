package com.devtime.audit;

import com.devtime.audit.dto.AuditLogRequests.AuditLogFilter;
import com.devtime.audit.dto.AuditLogResponses.AuditLogResponse;
import com.devtime.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Consulta da trilha de auditoria (spec 002 §22.2, users.md §10.1).
 *
 * <p>Somente leitura, por construção: INV-AUD-01 torna a trilha <i>append-only</i>, e esta
 * interface deliberadamente não declara nenhuma operação de escrita ou exclusão (CP-05). A escrita
 * pertence a {@link AuditService} e só ocorre dentro da transação da alteração auditada.
 */
public interface AuditLogService {

    /**
     * Página da trilha do tenant da sessão, do evento mais recente para o mais antigo.
     *
     * @param filter recorte opcional; sem intervalo, aplica os últimos 30 dias (CA-12)
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-3001} quando o
     *     intervalo excede 90 dias, {@code DEVTIME-2006} quando {@code size} excede 100
     */
    PageResponse<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable);
}
