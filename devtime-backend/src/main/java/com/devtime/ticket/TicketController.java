package com.devtime.ticket;

import com.devtime.shared.pagination.PageResponse;
import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.domain.TicketType;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketRequests.TicketUpdateRequest;
import com.devtime.ticket.dto.TicketResponses.TicketBoardResponse;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import com.devtime.ticket.dto.TicketResponses.TicketSummaryResponse;
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
 * Endpoints de ticket (tickets.md §5 a §7).
 *
 * <p>BR-080: nenhuma regra de negócio aqui. A permissão é verificada na camada de serviço (BR-161).
 * BR-089: as ações de máquina de estado ficam em {@link TicketTransitionController}, nunca como
 * {@code PATCH} no campo {@code status}.
 */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Tag(
        name = "Tickets",
        description = "Unidades de trabalho às quais todo registro de horas pertence")
public class TicketController {

    private final TicketService ticketService;
    private final TicketBoardService boardService;

    @GetMapping
    @Operation(
            summary = "Lista tickets com filtros compostos",
            description =
                    "Devolve projeção, sem `description` (BR-107). O filtro por etiquetas é"
                            + " conjuntivo: o ticket precisa possuir todas as informadas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de tickets"),
        @ApiResponse(responseCode = "400", description = "DEVTIME-2006 — size acima de 100")
    })
    public PageResponse<TicketSummaryResponse> list(
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) List<TicketStatus> status,
            @RequestParam(required = false) List<TicketType> type,
            @RequestParam(required = false) List<TicketPriority> priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID reporterId,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isOverEstimate,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ticketService.search(
                contractId,
                clientId,
                status,
                type,
                priority,
                assigneeId,
                reporterId,
                tagIds,
                search,
                isOverEstimate,
                pageable);
    }

    @GetMapping("/board")
    @Operation(
            summary = "Quadro agrupado por situação",
            description =
                    "Uma consulta agrupada, com no máximo 50 cartões por coluna. `totalCount`"
                            + " reflete o total real, permitindo indicar que há mais itens.")
    @ApiResponse(responseCode = "200", description = "Colunas do quadro")
    public TicketBoardResponse board(
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) UUID assigneeId) {
        return boardService.board(contractId, assigneeId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um ticket")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ticket encontrado"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — inexistente ou de outro tenant")
    })
    public TicketResponse getById(@PathVariable UUID id) {
        return ticketService.getById(id);
    }

    @GetMapping("/by-key/{key}")
    @Operation(
            summary = "Busca pela chave legível",
            description =
                    "A chave (`CT-0001-42`) é o identificador que circula em conversas, commits e"
                            + " e-mails. Chave malformada e de outro tenant respondem igualmente 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ticket encontrado"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002")
    })
    public TicketResponse getByKey(@PathVariable String key) {
        return ticketService.getByKey(key);
    }

    @PostMapping
    @Operation(
            summary = "Cria um ticket",
            description =
                    "RN-302: `number` e chave são gerados atomicamente e nunca mudam. `status`,"
                            + " `reporterId` e os totais estão ausentes do payload por construção.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Ticket criado, com a chave gerada"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — contrato de outro tenant"),
        @ApiResponse(
                responseCode = "422",
                description =
                        "DEVTIME-2301, DEVTIME-2303, DEVTIME-2304, DEVTIME-2306, DEVTIME-2104 ou"
                                + " DEVTIME-2313")
    })
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request) {
        TicketResponse created = ticketService.create(request);
        // BR-088: 201 sempre acompanha Location.
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Atualiza os campos descritivos",
            description =
                    "ME-05: `status` está ausente do payload — a situação muda apenas pelo endpoint"
                            + " de transição. `contractId` tem endpoint próprio, com guardas (RN-305).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ticket atualizado"),
        @ApiResponse(
                responseCode = "403",
                description = "DEVTIME-1103 — ticket de terceiro sem TICKET_UPDATE_ANY"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2004 — conflito de versão"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-2303, DEVTIME-2104 ou DEVTIME-2313")
    })
    public TicketResponse update(
            @PathVariable UUID id, @Valid @RequestBody TicketUpdateRequest request) {
        return ticketService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Exclui um ticket sem horas",
            description =
                    "RN-307: com horas registradas a exclusão é recusada e a mensagem orienta a"
                            + " cancelar — o cancelamento preserva as horas (RN-314).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Excluído logicamente"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2307 — ticket possui horas"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002")
    })
    public void delete(@PathVariable UUID id) {
        ticketService.delete(id);
    }
}
