package com.devtime.timer;

import com.devtime.timer.domain.Timer;
import com.devtime.timer.domain.TimerExceptions;
import com.devtime.timer.domain.TimerStatus;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Transições do cronômetro (state-machines.md §4.8).
 *
 * <p>BR-071/BR-072: a situação muda apenas por método de ação, e toda guarda é verificada antes de
 * qualquer efeito.
 *
 * <p><b>A transição mais importante desta máquina é a que não existe.</b> Uma falha de validação no
 * encerramento <b>não</b> transiciona o cronômetro (RN-160): ele permanece exatamente no estado em
 * que estava. Descartar tempo trabalhado por causa de um erro de configuração — uma sobreposição,
 * um saldo estourado, um contrato encerrado — é a proibição central da feature, e é o que sustenta
 * PV-03. Por isso o encerramento só chama {@link #markCompleted} <b>depois</b> de o work log
 * existir.
 */
@Component
public class TimerStateMachine {

    /** RN-153: pausar exige {@code RUNNING}. */
    public void assertCanPause(Timer timer) {
        assertNotTerminal(timer);
        if (timer.getStatus() != TimerStatus.RUNNING) {
            throw TimerExceptions.notRunning(timer.getStatus());
        }
    }

    /** RN-155: retomar exige {@code PAUSED}. */
    public void assertCanResume(Timer timer) {
        assertNotTerminal(timer);
        if (timer.getStatus() != TimerStatus.PAUSED) {
            throw TimerExceptions.notPaused(timer.getStatus());
        }
    }

    /** Encerrar e descartar exigem cronômetro ativo. */
    public void assertActive(Timer timer) {
        assertNotTerminal(timer);
        if (!timer.isActive()) {
            throw TimerExceptions.terminal(timer.getStatus());
        }
    }

    /** RN-165: apenas {@code ABANDONED} é recuperável. */
    public void assertRecoverable(Timer timer) {
        if (timer.getStatus() != TimerStatus.ABANDONED) {
            throw TimerExceptions.terminal(timer.getStatus());
        }
    }

    /**
     * ME-06: transições disponíveis a partir do estado atual, para a interface.
     *
     * <p>Devolve o que o <b>estado</b> permite; a permissão é filtrada por quem exibe.
     */
    public List<String> availableTransitions(TimerStatus status) {
        Set<String> transitions =
                switch (status) {
                    case RUNNING -> Set.of("PAUSE", "STOP", "DISCARD", "UPDATE");
                    case PAUSED -> Set.of("RESUME", "STOP", "DISCARD", "UPDATE");
                    case ABANDONED -> Set.of("RECOVER", "DISCARD");
                    case COMPLETED, DISCARDED -> Set.of();
                };
        return transitions.stream().sorted().toList();
    }

    private void assertNotTerminal(Timer timer) {
        if (timer.getStatus().isTerminal()) {
            throw TimerExceptions.terminal(timer.getStatus()); // ME-04
        }
    }
}
