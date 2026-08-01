package com.devtime.timer;

import com.devtime.audit.AuditService;
import com.devtime.shared.time.TenantClock;
import com.devtime.timer.domain.Timer;
import com.devtime.timer.domain.TimerStatus;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Descarte de cronômetros abandonados fora da janela de recuperação (RN-165).
 *
 * <p>Sete dias depois do início, um cronômetro abandonado deixa de ser recuperável: quem o esqueceu
 * já não consegue reconstituir com honestidade o horário real de término, e mantê-lo pendente
 * indefinidamente apenas acumularia ruído na lista de recuperáveis.
 *
 * <p>O descarte é <b>auditado com o tempo perdido</b> (§18): a única forma de responder mais tarde
 * por que aquelas horas nunca viraram registro.
 *
 * <p>BR-185: convergente — um cronômetro já {@code DISCARDED} não é selecionado pela consulta.
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class AbandonedTimerCleanupJob {

    private final TimerRepository repository;
    private final AuditService auditService;
    private final TenantClock clock;

    @Scheduled(cron = "0 45 3 * * *")
    @SchedulerLock(name = "timerCleanup", lockAtMostFor = "PT15M")
    @Transactional
    public void discardExpiredAbandoned() {
        var threshold = clock.now().minus(AbandonedTimerPolicy.RECOVERY_WINDOW);
        List<Timer> expired = repository.findAbandonedStartedBefore(threshold);

        for (Timer timer : expired) {
            int elapsedSeconds = timer.elapsedSeconds(clock.now());
            timer.setStatus(TimerStatus.DISCARDED);
            // INV-TMR-05: nenhum work log é gerado; workLogId permanece nulo.
            auditService.recordSystemAction(
                    "TIMER_DISCARDED_EXPIRED",
                    "Timer",
                    timer.getId(),
                    Map.of("status", TimerStatus.ABANDONED.name()),
                    Map.of("status", TimerStatus.DISCARDED.name()),
                    Map.of("discardedSeconds", elapsedSeconds));
        }

        if (!expired.isEmpty()) {
            log.warn(
                    "cronômetros abandonados descartados por expiração quantidade={}",
                    expired.size());
        }
    }
}
