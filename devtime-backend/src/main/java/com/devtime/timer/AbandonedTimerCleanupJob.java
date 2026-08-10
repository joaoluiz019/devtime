package com.devtime.timer;

import com.devtime.audit.AuditService;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
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
    private final TenantContext tenantContext;
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
            // A sessão de plataforma é estabelecida por cronômetro, e não uma vez para o lote: a
            // varredura atravessa tenants (BR-049), e a trilha resolve o tenant pelo contexto. Sem
            // isto, `recordSystemAction` lançava TenantContextNotInitializedException no primeiro
            // item e **nenhum** cronômetro expirado era descartado — a lista de recuperáveis
            // crescia para sempre. Mesma classe de defeito já corrigida em NotificationJobs e na
            // purga de organização.
            inTenant(
                    timer.getTenantId(),
                    () ->
                            // INV-TMR-05: nenhum work log é gerado; workLogId permanece nulo.
                            auditService.recordSystemAction(
                                    "TIMER_DISCARDED_EXPIRED",
                                    "Timer",
                                    timer.getId(),
                                    Map.of("status", TimerStatus.ABANDONED.name()),
                                    Map.of("status", TimerStatus.DISCARDED.name()),
                                    Map.of("discardedSeconds", elapsedSeconds)));
        }

        if (!expired.isEmpty()) {
            log.warn(
                    "cronômetros abandonados descartados por expiração quantidade={}",
                    expired.size());
        }
    }

    /** Sessão de plataforma para a iteração, restaurando a anterior ao final (CE-P-08). */
    private void inTenant(java.util.UUID tenantId, Runnable acao) {
        var anterior = tenantContext.session().orElse(null);
        tenantContext.set(
                TenantSession.system(tenantId, Role.OWNER, RolePermissions.of(Role.OWNER)));
        try {
            acao.run();
        } finally {
            if (anterior == null) {
                tenantContext.clear();
            } else {
                tenantContext.set(anterior);
            }
        }
    }
}
