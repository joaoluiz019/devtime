package com.devtime.worklog;

import java.util.UUID;

/**
 * Totais reais de um ticket, agregados dos registros de horas (RN-308).
 *
 * <p>Tipo de topo pelo mesmo motivo de {@code TagLinkCount}: Hibernate 6 não resolve o nome de uma
 * classe aninhada em {@code SELECT new}.
 */
public record TicketWorkLogTotal(UUID ticketId, long spentMinutes, long billableMinutes) {}
