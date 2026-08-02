package com.devtime.audit;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Manutenção da trilha (spec 002 §22.4).
 *
 * <p>BR-184/BR-185: bloqueio distribuído e idempotência por predicado. Ativo apenas no perfil
 * {@code scheduler}, como todo o agendamento (backend.md §13.1).
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class AuditJobs {

    private final AuditPartitionService partitionService;
    private final Clock clock;

    /**
     * Cria as partições do mês corrente e dos três seguintes.
     *
     * <p>Roda no primeiro dia de cada mês, mas a folga de três meses é o que realmente protege: se
     * uma execução falhar, ainda restam dois meses de partições antes de a trilha parar de gravar.
     */
    @Scheduled(cron = "0 0 1 1 * *")
    @SchedulerLock(name = "auditPartition", lockAtMostFor = "PT10M")
    public void createPartitions() {
        int ensured = partitionService.ensurePartitions(AuditPartitionService.today(clock));
        log.info("partições de auditoria garantidas quantidade={}", ensured);
    }
}
