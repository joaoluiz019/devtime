package com.devtime.audit;

import com.devtime.audit.dto.AuditLogRequests.AuditLogFilter;
import com.devtime.audit.dto.AuditLogResponses.AuditLogResponse;
import com.devtime.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta da trilha de auditoria (users.md §10.1).
 *
 * <p><b>Somente {@code GET}.</b> A ausência de qualquer outro verbo aqui é a implementação de
 * INV-AUD-01 e de CP-05 na camada HTTP: a trilha não é alterável por rota alguma. Acrescentar um
 * {@code POST}, {@code PATCH} ou {@code DELETE} nesta classe destrói o valor probatório da trilha
 * inteira (§19, SG-06).
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Auditoria", description = "Trilha append-only de alterações (RN-006, INV-AUD-01)")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(
            summary = "Consulta a trilha de auditoria",
            description =
                    "Sem intervalo informado, aplica os últimos 30 dias. O intervalo máximo por"
                            + " requisição é de 90 dias (users.md §10.1).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página da trilha, da mais recente"),
        @ApiResponse(
                responseCode = "400",
                description = "DEVTIME-3001 — intervalo acima de 90 dias"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — sem TENANT_AUDIT_VIEW")
    })
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant occurredTo,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return auditLogService.search(
                new AuditLogFilter(entityType, entityId, actorId, action, occurredFrom, occurredTo),
                pageable);
    }
}
