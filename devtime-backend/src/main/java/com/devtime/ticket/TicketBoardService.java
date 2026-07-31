package com.devtime.ticket;

import com.devtime.ticket.dto.TicketResponses.TicketBoardResponse;
import java.util.UUID;

/** Quadro (kanban) agrupado por situação (tickets.md §6.1). */
public interface TicketBoardService {

    /**
     * Quadro completo, servido por <b>uma</b> consulta agrupada.
     *
     * @param contractId escopo opcional por contrato
     * @param assigneeId escopo opcional por responsável
     */
    TicketBoardResponse board(UUID contractId, UUID assigneeId);
}
