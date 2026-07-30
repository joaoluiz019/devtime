package com.devtime.client;

import com.devtime.client.domain.ClientStatus;
import com.devtime.client.dto.ClientRequests.ClientCreateRequest;
import com.devtime.client.dto.ClientRequests.ClientUpdateRequest;
import com.devtime.client.dto.ClientRequests.DeactivateClientRequest;
import com.devtime.client.dto.ClientResponses.ClientDeactivationResponse;
import com.devtime.client.dto.ClientResponses.ClientListItemResponse;
import com.devtime.client.dto.ClientResponses.ClientResponse;
import com.devtime.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de cliente (clients.md §5 a §9).
 *
 * <p>BR-089: a mudança de situação usa {@code POST /{id}/activate} e {@code /deactivate}, nunca
 * {@code PATCH} no campo {@code status} (ME-05).
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Carteira de clientes do tenant (RN-401 a RN-407)")
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @Operation(
            summary = "Lista clientes",
            description =
                    "Busca sem acento e sem diferenciar caixa em nome, razão social e documento."
                            + " Para MEMBER, aplica-se o escopo de dados da nota ² de permissions.md.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de clientes"),
        @ApiResponse(responseCode = "400", description = "DEVTIME-2006 — size acima de 100")
    })
    public PageResponse<ClientListItemResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ClientStatus status,
            @RequestParam(required = false) Boolean hasActiveContracts,
            @RequestParam(required = false) String documentNumber,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        return clientService.search(search, status, hasActiveContracts, documentNumber, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um cliente com seus contatos e ações disponíveis")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — inexistente ou fora do escopo")
    })
    public ClientResponse getById(@PathVariable UUID id) {
        return clientService.getById(id);
    }

    @PostMapping
    @Operation(
            summary = "Cria um cliente",
            description = "RN-402 valida CPF/CNPJ; RN-403 e RN-404 garantem unicidade no tenant.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cliente criado"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2403 ou DEVTIME-2404"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2402 ou DEVTIME-2406")
    })
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientCreateRequest request) {
        ClientResponse created = clientService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/clients/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um cliente", description = "Exige version (RN-004).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cliente atualizado"),
        @ApiResponse(
                responseCode = "409",
                description = "DEVTIME-2004, DEVTIME-2403 ou DEVTIME-2404"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2402 — documento inválido")
    })
    public ClientResponse update(
            @PathVariable UUID id, @Valid @RequestBody ClientUpdateRequest request) {
        return clientService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Reativa um cliente", description = "state-machines.md §4.4.")
    @ApiResponse(
            responseCode = "200",
            description = "Cliente ativo; volta a aceitar novos contratos")
    public ClientResponse activate(@PathVariable UUID id) {
        return clientService.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(
            summary = "Inativa um cliente",
            description =
                    "RN-405 bloqueia novos contratos. RN-407: com contratos ativos, exige"
                            + " confirmActiveContracts = true; nenhum contrato é alterado.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Cliente inativo, com o impacto declarado"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2407 — confirmação obrigatória")
    })
    public ClientDeactivationResponse deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DeactivateClientRequest request) {
        return clientService.deactivate(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Exclui um cliente (exclusão lógica)",
            description = "RN-401: contratos ACTIVE ou SUSPENDED impedem a exclusão.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cliente excluído"),
        @ApiResponse(
                responseCode = "409",
                description = "DEVTIME-2401 — cliente com contrato ativo")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
