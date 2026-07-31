package com.devtime.tag.domain;

import java.io.Serializable;
import java.util.UUID;

/**
 * Chave primária composta de {@link TicketTagLink} (índice {@code pk_ticket_tags}).
 *
 * @param ticketId ticket rotulado
 * @param tagId etiqueta aplicada
 */
public record TicketTagLinkId(UUID ticketId, UUID tagId) implements Serializable {}
