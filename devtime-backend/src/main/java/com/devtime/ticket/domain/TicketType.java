package com.devtime.ticket.domain;

/** Natureza da unidade de trabalho (entities.md §6.12). Default {@code FEATURE}. */
public enum TicketType {
    FEATURE,
    BUG,
    SUPPORT,
    MEETING,
    MAINTENANCE,
    OTHER
}
