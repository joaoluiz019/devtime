package com.devtime.contract;

import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.dto.ContractResponses.ClientContractSummaryResponse;
import com.devtime.contract.dto.ContractResponses.ContractListItemResponse;
import com.devtime.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas de contrato aninhadas em cliente (clients.md §4 e §8).
 *
 * <p>Ficam nesta feature, e não em {@code 003-clients}, porque servem dados de contrato: {@code
 * 004} já depende de {@code 003} (RN-201), e implementá-las do lado do cliente fecharia um ciclo
 * entre os pacotes, proibido por AR-09. As rotas publicadas permanecem exatamente as documentadas.
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}")
@RequiredArgsConstructor
@Tag(name = "Contratos do cliente", description = "Visões de contrato agregadas por cliente")
public class ClientContractController {

    private final ContractService contractService;
    private final ClientContractSummaryService summaryService;

    @GetMapping("/contracts")
    @Operation(summary = "Lista os contratos do cliente")
    @ApiResponse(responseCode = "200", description = "Contratos do cliente")
    public PageResponse<ContractListItemResponse> contracts(
            @PathVariable UUID clientId,
            @RequestParam(required = false) ContractStatus status,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        return contractService.search(clientId, status, null, null, pageable);
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Resumo consolidado de consumo do cliente",
            description =
                    "clients.md §8. Campos monetários são omitidos sem CONTRACT_VIEW_FINANCIAL (SM-01).")
    @ApiResponse(responseCode = "200", description = "Totais e histórico por período")
    public ClientContractSummaryResponse summary(
            @PathVariable UUID clientId, @RequestParam(defaultValue = "6") int periods) {
        return summaryService.summarize(clientId, Math.min(periods, 24));
    }
}
