package com.devtime.timer;

import com.devtime.timer.domain.Timer;
import com.devtime.timer.dto.TimerResponses.AbandonedTimerResponse;
import com.devtime.timer.dto.TimerResponses.ActiveTimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerCategoryResponse;
import com.devtime.timer.dto.TimerResponses.TimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerTicketResponse;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Conversão de {@link Timer} para DTO.
 *
 * <p>BR-105: sem acesso a banco — ticket e categoria chegam já resolvidos pelo serviço.
 *
 * <p><b>{@code elapsedSeconds} não é calculado aqui</b> (§20, §21.3). O servidor devolve os três
 * campos de estado e o cliente deriva o tempo decorrido localmente; calcular no servidor forçaria o
 * cliente a consultá-lo a cada segundo para manter o número correndo.
 */
@Component
@RequiredArgsConstructor
public class TimerMapper {

    private final TimerStateMachine stateMachine;

    public TimerResponse toResponse(
            Timer timer, TimerTicketResponse ticket, TimerCategoryResponse category) {
        return new TimerResponse(
                timer.getId(),
                timer.getStatus().name(),
                ticket,
                category,
                timer.getStartedAt(),
                timer.getLastResumedAt(),
                timer.getAccumulatedActiveSeconds(),
                timer.getPausedMinutes(),
                timer.isBillable(),
                timer.getDescription(),
                timer.getStoppedAt(),
                timer.getWorkLogId(),
                stateMachine.availableTransitions(timer.getStatus()),
                timer.getVersion() == null ? 0L : timer.getVersion());
    }

    /** §19.1: sem descrição, sem pausas — apenas a existência do trabalho e o ticket. */
    public ActiveTimerResponse toActive(Timer timer, String userName, String ticketKey) {
        return new ActiveTimerResponse(
                timer.getId(),
                timer.getUserId(),
                userName,
                ticketKey,
                timer.getStatus().name(),
                timer.getStartedAt());
    }

    public AbandonedTimerResponse toAbandoned(
            Timer timer, TimerTicketResponse ticket, Instant now, LocalDate recoverableUntil) {
        return new AbandonedTimerResponse(
                timer.getId(),
                ticket,
                timer.getStartedAt(),
                timer.grossElapsedSeconds(now),
                recoverableUntil);
    }
}
