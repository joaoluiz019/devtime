package com.devtime.timer;

import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.time.TenantClock;
import com.devtime.tenant.TenantSettingsService;
import com.devtime.tenant.dto.TenantSettings;
import com.devtime.timer.domain.Timer;
import com.devtime.timer.domain.TimerStatus;
import com.devtime.timer.event.TimerEvents.TimerAbandonedEvent;
import com.devtime.timer.event.TimerEvents.TimerLongRunningEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detecção de cronômetros longos e abandonados (RN-163, RN-164).
 *
 * <p><b>O job não encerra cronômetros e não gera work logs</b> (RN-164). Encerrar exigiria inventar
 * um {@code endedAt}, e o sistema não decide quanto tempo alguém trabalhou (PR-03). Ele marca como
 * {@code ABANDONED} e notifica com a ação de recuperar informando o horário real — que é a única
 * pessoa capaz de fornecê-lo.
 *
 * <p>Percorre cronômetros de <b>todos</b> os tenants: o limiar vem de {@code tenant.settings} e
 * difere entre organizações, então cada cronômetro é avaliado contra a configuração da sua. A
 * leitura da configuração não exige {@code TenantContext} porque {@code tenants} é tabela global
 * (ART-013), sem filtro. BR-187: a falha em um tenant não interrompe os demais.
 *
 * <p>BR-185: idempotente. {@code longRunningNotifiedAt} impede a notificação duplicada (CX-05), e a
 * marcação de abandono é convergente — um cronômetro já {@code ABANDONED} não é reavaliado.
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class TimerMonitorJob {

    /** O limiar mais permissivo previsto em entities.md §6.1.1; a janela real é por tenant. */
    private static final Duration WIDEST_WINDOW = Duration.ofMinutes(480);

    private final TimerRepository repository;
    private final TenantSettingsService tenantSettingsService;
    private final DomainEventPublisher events;
    private final TenantClock clock;

    @Scheduled(cron = "0 */15 * * * *")
    @SchedulerLock(name = "timerMonitor", lockAtMostFor = "PT10M")
    @Transactional
    public void monitor() {
        Instant now = clock.now();
        // A varredura usa o menor limiar possível como filtro grosseiro; o limiar exato de cada
        // tenant é aplicado depois. Buscar sem filtro traria todos os cronômetros ativos do
        // sistema a cada 15 minutos.
        List<Timer> candidates = repository.findActiveStartedBefore(now.minus(WIDEST_WINDOW));

        // CE-B-05: cache por execução. Com dezenas de cronômetros no mesmo tenant, ler a
        // configuração uma vez por cronômetro multiplicaria consultas idênticas.
        Map<UUID, TenantSettings> settingsByTenant = new HashMap<>();

        int longRunning = 0;
        int abandoned = 0;
        for (Timer timer : candidates) {
            try {
                TenantSettings settings =
                        settingsByTenant.computeIfAbsent(
                                timer.getTenantId(), tenantSettingsService::settingsOf);
                long grossElapsed = timer.grossElapsedSeconds(now);

                // RN-164 primeiro: um cronômetro que já passou do abandono não precisa de alerta
                // de execução longa — ele passou dessa fase há horas.
                if (grossElapsed >= minutesToSeconds(settings.timerAutoAbandonMinutes())) {
                    timer.setStatus(TimerStatus.ABANDONED);
                    events.publish(
                            new TimerAbandonedEvent(
                                    timer.getId(), timer.getUserId(), grossElapsed));
                    log.info(
                            "cronômetro marcado como abandonado timerId={} grossElapsedSeconds={}",
                            timer.getId(),
                            grossElapsed);
                    abandoned++;
                    continue;
                }

                // RN-163: uma única vez por cronômetro. O carimbo é gravado antes da publicação,
                // então uma falha posterior não produz alerta duplicado na execução seguinte.
                if (timer.getLongRunningNotifiedAt() == null
                        && grossElapsed >= minutesToSeconds(settings.timerLongRunningMinutes())) {
                    timer.setLongRunningNotifiedAt(now);
                    events.publish(
                            new TimerLongRunningEvent(
                                    timer.getId(), timer.getUserId(), grossElapsed));
                    longRunning++;
                }
            } catch (RuntimeException failure) {
                // BR-187: um tenant com configuração corrompida não pode impedir a avaliação dos
                // cronômetros dos demais.
                log.error(
                        "falha ao avaliar cronômetro timerId={} tenantId={}",
                        timer.getId(),
                        timer.getTenantId(),
                        failure);
            }
        }

        // BR-188: métrica de itens processados em toda execução.
        log.info(
                "monitoramento de cronômetros concluído avaliados={} longos={} abandonados={}",
                candidates.size(),
                longRunning,
                abandoned);
    }

    private long minutesToSeconds(int minutes) {
        return Duration.ofMinutes(minutes).toSeconds();
    }
}
