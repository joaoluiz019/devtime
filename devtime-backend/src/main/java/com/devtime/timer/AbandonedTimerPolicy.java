package com.devtime.timer;

import com.devtime.shared.time.TenantClock;
import com.devtime.timer.domain.Timer;
import com.devtime.timer.domain.TimerExceptions;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Janela de recuperação do cronômetro abandonado (RN-165).
 *
 * <p>Sete dias equilibram duas coisas em tensão: recuperar tempo real trabalhado, que é o objetivo
 * de RN-164 ao não descartar o cronômetro, e higiene de dados — um cronômetro abandonado há meses
 * não tem mais como ser reconstituído com honestidade por quem o esqueceu.
 *
 * <p>Passada a janela, o cronômetro é descartado por job, e não silenciosamente ignorado: o
 * descarte é auditado com o tempo perdido (§18).
 */
@Component
@RequiredArgsConstructor
public class AbandonedTimerPolicy {

    /** RN-165. */
    public static final Duration RECOVERY_WINDOW = Duration.ofDays(7);

    private final TenantClock clock;

    /**
     * CX-08: o 7º dia ainda é recuperável; o 8º não.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2165} / {@code 409}
     */
    public void assertWithinRecoveryWindow(Timer timer) {
        Instant deadline = timer.getStartedAt().plus(RECOVERY_WINDOW);
        if (clock.now().isAfter(deadline)) {
            throw TimerExceptions.notRecoverable(recoverableUntil(timer));
        }
    }

    /** Prazo exibido na lista de abandonados, no fuso do tenant. */
    public LocalDate recoverableUntil(Timer timer) {
        return clock.toTenantDate(timer.getStartedAt().plus(RECOVERY_WINDOW));
    }
}
