package com.devtime.timer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs de entrada da feature 009 (worklogs.md §9 a §12).
 *
 * <p><b>Campos deliberadamente ausentes:</b> {@code userId} (OWN-05 — sempre o autenticado, SG-01),
 * {@code startedAt} e {@code lastResumedAt} (SG-05 — sempre do servidor; aceitá-los permitiria
 * inflar o tempo trabalhado com uma requisição), {@code accumulatedActiveSeconds} e {@code
 * pausedMinutes} (SG-06 — calculados), {@code status} (alterado apenas por endpoint de ação, ME-05)
 * e {@code workLogId}.
 */
public final class TimerRequests {

    private TimerRequests() {}

    /**
     * Início do cronômetro (RN-152).
     *
     * @param description opcional aqui; obrigatória apenas no encerramento (RN-158). Exigi-la no
     *     início criaria atrito na operação mais frequente do produto, e a natureza do trabalho
     *     costuma ficar clara durante a execução, não antes dela
     */
    @Schema(name = "TimerStartRequest")
    public record TimerStartRequest(
            @NotNull UUID ticketId,
            UUID categoryId,
            @Size(max = 2000) String description,
            Boolean billable) {}

    /** RN-161: ticket, categoria, descrição e faturável são editáveis durante a execução. */
    @Schema(name = "TimerUpdateRequest")
    public record TimerUpdateRequest(
            UUID ticketId,
            UUID categoryId,
            @Size(max = 2000) String description,
            Boolean billable) {}

    /**
     * Encerramento (RN-158).
     *
     * <p>A descrição é obrigatória e verificada <b>antes</b> de qualquer alteração de estado: é o
     * caminho de erro mais frequente, e o cronômetro precisa permanecer intocado nele.
     */
    @Schema(name = "TimerStopRequest")
    public record TimerStopRequest(@NotBlank @Size(min = 3, max = 2000) String description) {}

    /**
     * Recuperação de cronômetro abandonado (RN-165).
     *
     * <p>O horário de término é <b>informado pelo usuário</b>, nunca inventado pelo sistema: RN-164
     * marca como abandonado justamente para não encerrar com um valor arbitrário, o que violaria
     * PR-03. Uma descrição pode ser fornecida junto, quando o cronômetro ainda não a tinha.
     */
    @Schema(name = "TimerRecoverRequest")
    public record TimerRecoverRequest(
            @NotNull Instant endedAt, @Size(max = 2000) String description) {}
}
