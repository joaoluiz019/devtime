package com.devtime.ticket;

import com.devtime.ticket.dto.TicketResponses.TicketActivityResponse;
import java.time.Instant;
import java.util.UUID;

/** Linha do tempo unificada do ticket (tickets.md §9.1). */
public interface TicketActivityService {

    /**
     * Eventos do ticket em ordem cronológica decrescente, paginados por <b>cursor</b>.
     *
     * <p>Cursor e não {@code OFFSET}: um ticket com mil eventos teria a última página
     * progressivamente mais lenta com deslocamento, porque o banco precisa descartar as linhas
     * anteriores a cada requisição.
     *
     * @param cursor instante do último evento da página anterior; nulo na primeira página
     */
    TicketActivityResponse activity(UUID ticketId, Instant cursor, int size);
}
