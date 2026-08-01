package com.devtime.worklog;

import com.devtime.shared.pagination.PageResponse;
import com.devtime.worklog.dto.WorkLogFilter;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogDuplicateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogUpdateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogValidateRequest;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCreatedResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogSummaryResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogValidateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de registro de horas (worklogs.md §5 e §6).
 *
 * <p>BR-080: nenhuma regra de negócio aqui. Em particular, {@code LockedPeriodGuard} e a política
 * de propriedade são verificados no <b>serviço</b> (IMP-01, SG-05): verificar apenas na fronteira
 * HTTP deixaria o caminho do cronômetro (RN-159) sem proteção.
 */
@RestController
@RequestMapping("/api/v1/work-logs")
@RequiredArgsConstructor
@Tag(
        name = "Work Logs",
        description = "Registro de horas — o dado que o cliente compra e a linha do relatório")
public class WorkLogController {

    private final WorkLogService workLogService;
    private final WorkLogValidationService validationService;

    @GetMapping
    @Operation(
            summary = "Lista registros de horas com filtros compostos",
            description =
                    "Devolve projeção sem `description` (BR-107). `MEMBER` enxerga apenas os"
                            + " próprios registros, com o escopo aplicado na consulta — inclusive na"
                            + " contagem da paginação (§9 de permissions.md).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de registros"),
        @ApiResponse(responseCode = "400", description = "DEVTIME-2006 — size acima de 100")
    })
    public PageResponse<WorkLogSummaryResponse> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID ticketId,
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Boolean billable,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return workLogService.search(
                new WorkLogFilter(
                        userId,
                        ticketId,
                        contractId,
                        clientId,
                        categoryId,
                        tagIds,
                        dateFrom,
                        dateTo,
                        billable,
                        source,
                        search),
                pageable);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Detalha um registro de horas",
            description =
                    "CE-P-04: registro de outro tenant e registro de colega para `MEMBER` respondem"
                            + " igualmente 404 — a existência já é informação sobre quem trabalhou em"
                            + " quê.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Registro encontrado"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002")
    })
    public WorkLogResponse getById(@PathVariable UUID id) {
        return workLogService.getById(id);
    }

    @PostMapping
    @Operation(
            summary = "Registra horas manualmente",
            description =
                    "Aplica integralmente a ordem normativa da §6.1. A resposta traz o saldo do"
                            + " período **já atualizado** e, sob `OveragePolicy = WARN`, o aviso"
                            + " DEVTIME-2221 em `warnings[]`. `contractId`, `clientId`, `netMinutes`,"
                            + " `source` e `timerId` estão ausentes do payload por construção.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Registro criado, com o saldo atualizado"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — RN-106 sem permissão"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — ticket de outro tenant"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2121 — período fechado"),
        @ApiResponse(
                responseCode = "422",
                description =
                        "DEVTIME-2102 (sobreposição), 2103, 2104, 2105, 2107, 2114, 2115, 2116,"
                                + " 2117, 2118, 2119, 2120, 2220 (saldo com BLOCK), 2306 ou 2313")
    })
    public ResponseEntity<WorkLogCreatedResponse> create(
            @Valid @RequestBody WorkLogCreateRequest request) {
        WorkLogCreatedResponse created = workLogService.create(request);
        // BR-088: 201 sempre acompanha Location.
        return ResponseEntity.created(URI.create("/api/v1/work-logs/" + created.workLog().id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Edita um registro de horas",
            description =
                    "Revalida a §6.1 integralmente e incrementa `editCount` (RN-123). Registro de"
                            + " período fechado responde 409 DEVTIME-2121 — nem o autor o edita"
                            + " (OWN-02); a correção exige reabertura formal do período.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Registro atualizado com o saldo"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1103 — registro de terceiro"),
        @ApiResponse(
                responseCode = "409",
                description = "DEVTIME-2004, DEVTIME-2121 ou DEVTIME-2124"),
        @ApiResponse(responseCode = "422", description = "Mesmos códigos da criação")
    })
    public WorkLogCreatedResponse update(
            @PathVariable UUID id, @Valid @RequestBody WorkLogUpdateRequest request) {
        return workLogService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Exclui logicamente um registro de horas",
            description =
                    "RN-125: devolve o saldo ao período e reduz `ticket.spentMinutes` na mesma"
                            + " transação. A notificação de limiar já emitida **não** é removida"
                            + " (CE-11).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Excluído logicamente"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2121 — período fechado"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002")
    })
    public void delete(@PathVariable UUID id) {
        workLogService.delete(id);
    }

    @PostMapping("/{id}/duplicate")
    @Operation(
            summary = "Duplica um registro em novo horário",
            description =
                    "FA-14: copia ticket, categoria, descrição, faturável e etiquetas. O horário é"
                            + " obrigatoriamente novo — duplicar mantendo o intervalo cria"
                            + " sobreposição e é rejeitado por RN-102 (CX-28).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cópia criada"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2102 e demais códigos da criação")
    })
    public ResponseEntity<WorkLogCreatedResponse> duplicate(
            @PathVariable UUID id, @Valid @RequestBody WorkLogDuplicateRequest request) {
        WorkLogCreatedResponse created = workLogService.duplicate(id, request);
        return ResponseEntity.created(URI.create("/api/v1/work-logs/" + created.workLog().id()))
                .body(created);
    }

    @PostMapping("/validate")
    @Operation(
            summary = "Valida um registro sem persistir",
            description =
                    "FA-01 / CP-19: **nada é gravado**. Diferente da criação, relata todos os"
                            + " problemas encontrados de uma vez, com os conflitos de sobreposição, o"
                            + " cálculo resultante e a prévia do saldo.")
    @ApiResponse(responseCode = "200", description = "Resultado da validação, sem efeito colateral")
    public WorkLogValidateResponse validate(@Valid @RequestBody WorkLogValidateRequest request) {
        return validationService.validate(request);
    }
}
