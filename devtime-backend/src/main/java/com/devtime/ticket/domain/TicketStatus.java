package com.devtime.ticket.domain;

/**
 * Situação do ticket (entities.md §6.12, state-machines.md §4.7).
 *
 * <p>Sete estados. {@code DONE} e {@code CANCELLED} são terminais <b>reversíveis</b>: o primeiro
 * reabre para {@code IN_PROGRESS} ou {@code IN_REVIEW}, o segundo reativa para {@code BACKLOG}
 * quando o contrato permite.
 */
public enum TicketStatus {

    /** Registrado, ainda não priorizado. Estado inicial de toda criação. */
    BACKLOG,

    /** Priorizado, aguardando início. */
    TODO,

    /** Em execução. A primeira entrada preenche {@code startedAt} (RN-310). */
    IN_PROGRESS,

    /** Impedido por dependência externa. Exige {@code blockReason} com no mínimo 5 caracteres. */
    BLOCKED,

    /** Aguardando validação. */
    IN_REVIEW,

    /** Concluído. Preenche {@code completedAt}; sair limpa o campo (RN-310, INV-TCK-04). */
    DONE,

    /** Descartado. Os work logs são preservados e nenhuma hora volta ao saldo (RN-314). */
    CANCELLED
}
