package com.devtime.timer.dto;

import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogWarning;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs de saída da feature 009 (worklogs.md §9 a §12; spec §23). */
public final class TimerResponses {

    private TimerResponses() {}

    @Schema(name = "TimerTicketResponse")
    public record TimerTicketResponse(UUID id, String key, String title) {}

    @Schema(name = "TimerCategoryResponse")
    public record TimerCategoryResponse(UUID id, String name, String color) {}

    /**
     * Estado do cronômetro.
     *
     * <p><b>{@code elapsedSeconds} é calculado no cliente</b>, a partir de {@code startedAt},
     * {@code lastResumedAt} e {@code accumulatedActiveSeconds} (§20, §21.3). O servidor devolve o
     * <b>estado</b>; ele não é o relógio. Um cronômetro que consultasse o servidor a cada segundo
     * geraria 3.600 requisições por hora por pessoa ativa — insustentável e desnecessário.
     *
     * @param availableTransitions ME-06 — o que o estado atual permite
     */
    @Schema(name = "TimerResponse")
    public record TimerResponse(
            UUID id,
            String status,
            TimerTicketResponse ticket,
            TimerCategoryResponse category,
            Instant startedAt,
            Instant lastResumedAt,
            int accumulatedActiveSeconds,
            int pausedMinutes,
            boolean billable,
            String description,
            Instant stoppedAt,
            UUID workLogId,
            List<String> availableTransitions,
            long version) {}

    /**
     * Encerramento bem-sucedido: o work log gerado e o saldo já atualizado.
     *
     * <p>Herda os avisos de {@code 008} (RN-232): o excedente é do registro de horas, não do
     * cronômetro.
     */
    @Schema(name = "TimerStopResponse")
    public record TimerStopResponse(
            TimerResponse timer,
            WorkLogResponse workLog,
            PeriodBalanceResponse balance,
            List<WorkLogWarning> warnings) {}

    /**
     * Cronômetro ativo de um colega ({@code GET /timers/active}, {@code TIMER_VIEW_ANY}).
     *
     * <p>§19.1: <b>sem descrição e sem histórico de pausas</b>. A visão da equipe mostra que existe
     * trabalho em andamento e em qual ticket — não o ritmo de trabalho de uma pessoa, que é o dado
     * mais íntimo que o produto coleta.
     */
    @Schema(name = "ActiveTimerResponse")
    public record ActiveTimerResponse(
            UUID id,
            UUID userId,
            String userName,
            String ticketKey,
            String status,
            Instant startedAt) {}

    /**
     * Cronômetro abandonado, recuperável (RN-165).
     *
     * @param recoverableUntil prazo no fuso do tenant; depois dele o descarte é definitivo
     */
    @Schema(name = "AbandonedTimerResponse")
    public record AbandonedTimerResponse(
            UUID id,
            TimerTicketResponse ticket,
            Instant startedAt,
            long grossElapsedSeconds,
            LocalDate recoverableUntil) {}
}
