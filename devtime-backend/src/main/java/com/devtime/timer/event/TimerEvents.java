package com.devtime.timer.event;

import com.devtime.shared.event.DomainEvent;
import java.util.UUID;

/**
 * Eventos da feature 009 (spec §15).
 *
 * <p>BR-180/BR-181: {@code record} imutável com identificadores.
 */
public final class TimerEvents {

    private TimerEvents() {}

    /** Telemetria de início — alimenta a métrica de adoção do cronômetro. */
    public record TimerStartedEvent(UUID timerId, UUID ticketId, UUID userId)
            implements DomainEvent {}

    /** Encerramento bem-sucedido; {@code 010-dashboard} atualiza o painel. */
    public record TimerCompletedEvent(UUID timerId, UUID workLogId, int netMinutes)
            implements DomainEvent {}

    /**
     * RN-163: cronômetro ultrapassou o limiar de execução longa.
     *
     * <p>Emitido <b>uma única vez</b> por cronômetro — {@code longRunningNotifiedAt} é gravado
     * antes da publicação, e é ele que impede a duplicação na execução seguinte do job (CX-05).
     */
    public record TimerLongRunningEvent(
            UUID timerId, UUID userId, UUID ticketId, long grossElapsedSeconds)
            implements DomainEvent {}

    /**
     * RN-164: marcado como abandonado; a notificação leva a ação de recuperar.
     *
     * @param recoverableUntil RN-165 — prazo exibido na notificação. Viaja no evento porque quem o
     *     conhece é {@code AbandonedTimerPolicy}, dentro desta feature; recalculá-lo no consumidor
     *     duplicaria a janela de 7 dias em dois lugares
     */
    public record TimerAbandonedEvent(
            UUID timerId,
            UUID userId,
            UUID ticketId,
            long grossElapsedSeconds,
            java.time.LocalDate recoverableUntil)
            implements DomainEvent {}

    /**
     * RN-162: descarte, com o tempo descartado.
     *
     * <p>É a única operação do sistema que destrói trabalho registrado sem contrapartida, e este
     * evento — junto com a auditoria — é o que permite responder "por que faltam 3 horas naquela
     * terça".
     */
    public record TimerDiscardedEvent(UUID timerId, UUID userId, int elapsedSeconds)
            implements DomainEvent {}

    /** OWN-05 / SG-07: encerramento forçado por {@code ADMIN}; o <b>dono</b> é notificado. */
    public record TimerForceStoppedEvent(UUID timerId, UUID ownerId, UUID ticketId, UUID stoppedBy)
            implements DomainEvent {}
}
