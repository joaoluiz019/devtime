package com.devtime.ticket;

import com.devtime.ticket.dto.TicketResponses.TicketActivityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Linha do tempo do ticket (tickets.md §9.1). */
@RestController
@RequestMapping("/api/v1/tickets/{id}/activity")
@RequiredArgsConstructor
@Tag(name = "Tickets — atividade", description = "Linha do tempo unificada do ticket")
public class TicketActivityController {

    private final TicketActivityService activityService;

    @GetMapping
    @Operation(
            summary = "Linha do tempo do ticket",
            description =
                    "Une auditoria e comentários em ordem cronológica decrescente, paginada por"
                            + " cursor. Registros de horas de outros usuários são omitidos para quem"
                            + " não possui WORKLOG_VIEW_ANY (§9 de permissions.md).")
    @ApiResponse(responseCode = "200", description = "Eventos do ticket")
    public TicketActivityResponse activity(
            @PathVariable UUID id,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(defaultValue = "50") int size) {
        return activityService.activity(id, cursor, size);
    }
}
