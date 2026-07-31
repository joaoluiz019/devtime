package com.devtime.ticket;

import com.devtime.ticket.dto.TicketRequests.TicketAssignRequest;
import com.devtime.ticket.dto.TicketRequests.TicketMoveContractRequest;
import com.devtime.ticket.dto.TicketRequests.TicketTransitionRequest;
import com.devtime.ticket.dto.TicketResponses.TicketMoveContractResponse;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ações de máquina de estado do ticket (tickets.md §8).
 *
 * <p>BR-089 / ME-05: {@code POST /{recurso}/{id}/{ação}}, nunca {@code PATCH} no campo {@code
 * status}. Um {@code PATCH} transformaria uma operação com guardas e efeitos em uma escrita de
 * campo e abriria caminho para estados inconsistentes.
 */
@RestController
@RequestMapping("/api/v1/tickets/{id}")
@RequiredArgsConstructor
@Tag(name = "Tickets — transições", description = "Situação, responsável e contrato do ticket")
public class TicketTransitionController {

    private final TicketTransitionService transitionService;
    private final TicketService ticketService;

    @PostMapping("/transition")
    @Operation(
            summary = "Muda a situação do ticket",
            description =
                    "Aplica a matriz de state-machines.md §4.7. `blockReason` é obrigatório apenas"
                            + " para BLOCKED. Concluir com cronômetro ativo — inclusive pausado — é"
                            + " bloqueado (RN-311).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Situação alterada"),
        @ApiResponse(
                responseCode = "403",
                description = "DEVTIME-1103 — MEMBER transicionando ticket alheio"),
        @ApiResponse(
                responseCode = "409",
                description =
                        "DEVTIME-2010 (fora da matriz, com availableTransitions), DEVTIME-2311"
                                + " (cronômetro ativo) ou DEVTIME-2004 (versão)"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2314 — motivo do impedimento")
    })
    public TicketResponse transition(
            @PathVariable UUID id, @Valid @RequestBody TicketTransitionRequest request) {
        return transitionService.transition(id, request);
    }

    @PostMapping("/assign")
    @Operation(
            summary = "Atribui ou remove o responsável",
            description =
                    "`assigneeId` nulo remove o responsável, sem notificação (FA-05). A"
                            + " reatribuição notifica apenas o novo responsável (FA-04).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Responsável alterado"),
        @ApiResponse(
                responseCode = "403",
                description = "DEVTIME-1103 — ticket alheio para MEMBER"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2304 — responsável inválido")
    })
    public TicketResponse assign(
            @PathVariable UUID id, @Valid @RequestBody TicketAssignRequest request) {
        return transitionService.assign(id, request);
    }

    @PostMapping("/move-contract")
    @Operation(
            summary = "Move o ticket para outro contrato do mesmo cliente",
            description =
                    "RN-305: apenas sem horas registradas e dentro do mesmo cliente. `number` e a"
                            + " chave legível permanecem inalterados (RN-011) — a chave já circulou"
                            + " fora do sistema e é referência externa permanente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrato alterado; a chave não muda"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2305 — ticket possui horas"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-2315 (outro cliente) ou DEVTIME-2306 (destino encerrado)")
    })
    public TicketMoveContractResponse moveContract(
            @PathVariable UUID id, @Valid @RequestBody TicketMoveContractRequest request) {
        return ticketService.moveContract(id, request);
    }
}
