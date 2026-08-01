package com.devtime.timer;

import com.devtime.shared.time.TenantClock;
import com.devtime.timer.domain.Timer;
import com.devtime.timer.domain.TimerPause;
import com.devtime.timer.domain.TimerStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Abertura e fechamento de pausas (RN-154, RN-156, INV-TMR-02, INV-TMR-03).
 *
 * <p>Concentra as duas operações no mesmo lugar porque elas são inversas e precisam permanecer
 * simétricas: qualquer divergência entre o que a pausa acumula e o que a retomada recalcula vira
 * tempo cobrado a mais ou a menos.
 *
 * <p>RN-157: <b>não há limite para o número de pausas</b> (CX-12). O limite existe sobre a
 * <b>soma</b>, verificada no encerramento por RN-116 — pausar cinquenta vezes é comportamento
 * legítimo de quem trabalha em ambiente interrompido; pausar mais tempo do que a sessão inteira é
 * incoerência aritmética.
 */
@Component
@RequiredArgsConstructor
public class TimerPausePolicy {

    private final TimerPauseRepository pauseRepository;
    private final TenantClock clock;

    /**
     * RN-154: consolida o trecho ativo, muda para {@code PAUSED} e abre a pausa.
     *
     * <p>A ordem importa: o trecho ativo é acumulado <b>antes</b> da troca de estado, porque {@code
     * elapsedSeconds} deixa de contar {@code now − lastResumedAt} assim que o estado sai de {@code
     * RUNNING}. Inverter congelaria o cronômetro perdendo o trecho corrente.
     */
    public TimerPause pause(Timer timer) {
        Instant now = clock.now();
        timer.setAccumulatedActiveSeconds(
                timer.getAccumulatedActiveSeconds()
                        + (int) Duration.between(timer.getLastResumedAt(), now).toSeconds());
        timer.setStatus(TimerStatus.PAUSED);

        TimerPause pause = new TimerPause();
        pause.setTimerId(timer.getId());
        pause.setPausedAt(now);
        return pauseRepository.save(pause);
    }

    /**
     * RN-156: fecha a pausa aberta, recalcula {@code pausedMinutes} e retoma a contagem.
     *
     * <p>{@code pausedMinutes} é <b>recalculado pela soma real</b> das pausas concluídas, nunca
     * incrementado: um incremento perdido produziria um total menor que o tempo efetivamente
     * parado, e a diferença seria cobrada do cliente (PR-03).
     */
    public void resume(Timer timer) {
        Instant now = clock.now();
        closeOpenPause(timer, now);
        timer.setPausedMinutes(recalculatePausedMinutes(timer));
        timer.setLastResumedAt(now);
        timer.setStatus(TimerStatus.RUNNING);
    }

    /**
     * RN-159 passo 4: fecha a pausa aberta antes do encerramento, quando houver.
     *
     * <p>Chamado <b>depois</b> da verificação da descrição (passo 3), porque fechar a pausa altera
     * estado persistido e rejeitar em seguida exigiria desfazê-lo. Validar antes mantém o
     * cronômetro intocado no caminho de erro mais frequente.
     */
    public void closeForStop(Timer timer, Instant stoppedAt) {
        if (timer.getStatus() == TimerStatus.PAUSED) {
            closeOpenPause(timer, stoppedAt);
        } else {
            timer.setAccumulatedActiveSeconds(
                    timer.getAccumulatedActiveSeconds()
                            + (int)
                                    Duration.between(timer.getLastResumedAt(), stoppedAt)
                                            .toSeconds());
        }
        timer.setPausedMinutes(recalculatePausedMinutes(timer));
    }

    private void closeOpenPause(Timer timer, Instant closedAt) {
        Optional<TimerPause> open = pauseRepository.findOpenByTimer(timer.getId());
        open.ifPresent(
                pause -> {
                    pause.setResumedAt(closedAt);
                    pause.setDurationSeconds(
                            (int) Duration.between(pause.getPausedAt(), closedAt).toSeconds());
                });
    }

    /** RN-010: minutos inteiros, com segundos truncados — nunca arredondados (BR-144). */
    private int recalculatePausedMinutes(Timer timer) {
        return (int) (pauseRepository.sumDurationSecondsByTimer(timer.getId()) / 60);
    }
}
