package com.devtime.contract;

import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.dto.ContractRequests.ContractCreateRequest;
import com.devtime.contract.dto.ContractRequests.ContractDuplicateRequest;
import com.devtime.contract.dto.ContractRequests.ContractTransitionRequest;
import com.devtime.contract.dto.ContractRequests.ContractUpdateRequest;
import com.devtime.contract.dto.ContractRequests.PeriodPreviewRequest;
import com.devtime.contract.dto.ContractResponses.ContractActivationResponse;
import com.devtime.contract.dto.ContractResponses.ContractHistoryResponse;
import com.devtime.contract.dto.ContractResponses.ContractListItemResponse;
import com.devtime.contract.dto.ContractResponses.ContractPeriodResponse;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.contract.dto.ContractResponses.ContractTransitionResponse;
import com.devtime.contract.dto.ContractResponses.PeriodPreviewResponse;
import com.devtime.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de contrato (contracts.md §5 a §8 e §12.2).
 *
 * <p>BR-089 / ME-05: as transições usam {@code POST /{id}/{ação}}; {@code status} não existe em
 * nenhum DTO de entrada e é ignorado se enviado.
 */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contratos", description = "Contratos e ciclo de períodos (RN-201 a RN-217)")
public class ContractController {

    private final ContractService contractService;
    private final ContractPreviewService previewService;
    private final ContractPeriodService periodService;

    @GetMapping
    @Operation(summary = "Lista contratos com filtros")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de contratos"),
        @ApiResponse(responseCode = "400", description = "DEVTIME-2006 — size acima de 100")
    })
    public PageResponse<ContractListItemResponse> list(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) ContractType type,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        return contractService.search(clientId, status, type, search, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um contrato com período corrente e transições disponíveis")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrato encontrado"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — inexistente ou de outro tenant")
    })
    public ContractResponse getById(@PathVariable UUID id) {
        return contractService.getById(id);
    }

    @PostMapping
    @Operation(
            summary = "Cria um contrato em DRAFT",
            description =
                    "RN-201 exige cliente ACTIVE. Nenhum período é gerado antes da ativação;"
                            + " a resposta traz periodsPreview para conferência.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Contrato criado em DRAFT"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2206 — código já existe"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-2201, 2202, 2203, 2204, 2209 ou 2210")
    })
    public ResponseEntity<ContractResponse> create(
            @Valid @RequestBody ContractCreateRequest request) {
        ContractResponse created = contractService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/contracts/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/duplicate")
    @Operation(
            summary = "Duplica um contrato em DRAFT",
            description =
                    "Copia a configuração — nunca períodos, saldos nem horas — e gera código novo"
                            + " (INV-CTR-01). O corpo é opcional: informe apenas o que muda.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cópia criada em DRAFT"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — contrato inexistente"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-2201 — cliente da cópia não está ACTIVE")
    })
    public ResponseEntity<ContractResponse> duplicate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ContractDuplicateRequest request) {
        ContractResponse created = contractService.duplicate(id, request);
        return ResponseEntity.created(URI.create("/api/v1/contracts/" + created.id()))
                .body(created);
    }

    @PostMapping("/preview-periods")
    @Operation(
            summary = "Calcula a prévia de períodos sem persistir",
            description =
                    "Mesmo algoritmo da ativação (CA-01): a prévia não pode divergir do gerado.")
    @ApiResponse(responseCode = "200", description = "Períodos projetados")
    public PeriodPreviewResponse previewPeriods(@Valid @RequestBody PeriodPreviewRequest request) {
        return previewService.preview(request);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Atualiza um contrato",
            description =
                    "RN-207: alterar monthlyMinutes exige applyToCurrentPeriod e nunca atinge período"
                            + " fechado. RN-208: o ciclo não muda com horas lançadas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrato atualizado"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2004, 2207 ou 2208"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2003 — campo imutável")
    })
    public ContractResponse update(
            @PathVariable UUID id, @Valid @RequestBody ContractUpdateRequest request) {
        return contractService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @Operation(
            summary = "Ativa o contrato",
            description = "RN-209: gera o primeiro período como OPEN, na mesma transação.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrato ativo com o primeiro período"),
        @ApiResponse(
                responseCode = "409",
                description = "DEVTIME-2010 — contrato não está em DRAFT"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2201 ou DEVTIME-2211")
    })
    public ContractActivationResponse activate(@PathVariable UUID id) {
        return contractService.activate(id);
    }

    @PostMapping("/{id}/suspend")
    @Operation(
            summary = "Suspende o contrato",
            description =
                    "Exige motivo com no mínimo 10 caracteres. O período aberto permanece aberto.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrato suspenso"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2010 — transição inválida"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2215 — justificativa obrigatória")
    })
    public ContractTransitionResponse suspend(
            @PathVariable UUID id, @Valid @RequestBody ContractTransitionRequest request) {
        return contractService.suspend(id, request);
    }

    @PostMapping("/{id}/resume")
    @Operation(
            summary = "Retoma o contrato",
            description = "CE-ME-09: gera os períodos faltantes preservando a contiguidade.")
    @ApiResponse(responseCode = "200", description = "Contrato ativo, com os períodos gerados")
    public ContractTransitionResponse resume(@PathVariable UUID id) {
        return contractService.resume(id);
    }

    @PostMapping("/{id}/end")
    @Operation(
            summary = "Encerra o contrato",
            description =
                    "RN-214: trunca o período corrente em endDate; nenhum posterior é gerado.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrato encerrado"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2213 — data de término inválida")
    })
    public ContractTransitionResponse end(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ContractTransitionRequest request) {
        return contractService.end(id, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancela o contrato (distrato)",
            description =
                    "Transição terminal. Trunca o período corrente em hoje; registros são preservados.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrato cancelado"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2215 — justificativa obrigatória")
    })
    public ContractTransitionResponse cancel(
            @PathVariable UUID id, @Valid @RequestBody ContractTransitionRequest request) {
        return contractService.cancel(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Exclui um contrato",
            description =
                    "RN-205: permitido apenas em DRAFT; nos demais estados, use end ou cancel.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Contrato excluído"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2205 — contrato fora de DRAFT")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        contractService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/periods")
    @Operation(summary = "Lista os períodos do contrato")
    @ApiResponse(responseCode = "200", description = "Períodos em ordem de sequência")
    public List<ContractPeriodResponse> periods(@PathVariable UUID id) {
        return periodService.listByContract(id);
    }

    @GetMapping("/{id}/history")
    @Operation(
            summary = "Série histórica dos períodos",
            description = "contracts.md §12.2. Máximo de 24 períodos.")
    @ApiResponse(responseCode = "200", description = "Histórico com agregados")
    public ContractHistoryResponse history(
            @PathVariable UUID id, @RequestParam(defaultValue = "12") int periods) {
        return contractService.history(id, Math.min(periods, 24));
    }
}
