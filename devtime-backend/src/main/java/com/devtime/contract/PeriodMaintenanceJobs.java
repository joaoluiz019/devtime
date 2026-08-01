package com.devtime.contract;

import com.devtime.audit.AuditService;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodSnapshot;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.shared.time.TenantClock;
import java.time.Duration;
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
 * Jobs de manutenção do banco de horas — feature 011 (spec §22.4).
 *
 * <p>Reunidos porque compartilham natureza — varredura por predicado sobre o estado atual, sem
 * parâmetro — e separá-los multiplicaria cerimônia sem separar responsabilidade, como já se fez em
 * {@code AuthCleanupJobs}.
 *
 * <p>BR-185: todos convergentes. Reexecutar não produz efeito diferente porque operam sobre o
 * estado corrente, não sobre um delta acumulado.
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class PeriodMaintenanceJobs {

    /** CE-ME-07: um período em {@code CLOSING} por mais que isso indica falha de infraestrutura. */
    private static final Duration STUCK_CLOSING_THRESHOLD = Duration.ofMinutes(10);

    private final ContractPeriodRepository periodRepository;
    private final PeriodSnapshotRepository snapshotRepository;
    private final SnapshotBuilder snapshotBuilder;
    private final AuditService auditService;
    private final TenantClock clock;

    /**
     * CE-ME-07 / CX-15: reverte para {@code OPEN} períodos presos em {@code CLOSING}.
     *
     * <p>Um período em {@code CLOSING} bloqueia toda escrita naquele ciclo. Se a aplicação caiu no
     * meio do fechamento, a transação foi revertida no banco mas o status pode ter ficado — e
     * ninguém consegue registrar horas até alguém perceber. Reverter para {@code OPEN} é seguro
     * justamente porque a transação de fechamento é atômica: ou ela concluiu e o status é {@code
     * CLOSED}, ou nada dela foi aplicado.
     */
    @Scheduled(cron = "0 */10 * * * *")
    @SchedulerLock(name = "stuckClosing", lockAtMostFor = "PT5M")
    @Transactional
    public void revertStuckClosings() {
        List<ContractPeriod> stuck =
                periodRepository.findStuckClosing(clock.now().minus(STUCK_CLOSING_THRESHOLD));
        for (ContractPeriod period : stuck) {
            period.setStatus(PeriodStatus.OPEN);
            auditService.recordSystemAction(
                    "PERIOD_CLOSING_REVERTED",
                    "ContractPeriod",
                    period.getId(),
                    Map.of("status", PeriodStatus.CLOSING.name()),
                    Map.of("status", PeriodStatus.OPEN.name()),
                    Map.of("thresholdMinutes", STUCK_CLOSING_THRESHOLD.toMinutes()));
            // ERROR com alerta operacional: significa que um fechamento foi interrompido.
            log.error(
                    "período preso em CLOSING revertido para OPEN periodId={} contractId={}",
                    period.getId(),
                    period.getContractId());
        }
    }

    /**
     * SG-05 / CX-21: verifica os checksums dos snapshots e <b>alerta sem corrigir</b>.
     *
     * <p>Reescrever um snapshot para "acertar" o checksum destruiria a única evidência de que algo
     * foi alterado — e o snapshot existe justamente para ser essa evidência.
     */
    @Scheduled(cron = "0 0 5 * * 0")
    @SchedulerLock(name = "snapshotIntegrity", lockAtMostFor = "PT60M")
    @Transactional(readOnly = true)
    public void verifySnapshotIntegrity() {
        List<PeriodSnapshot> snapshots = snapshotRepository.findAll();
        long invalid =
                snapshots.stream()
                        .filter(
                                snapshot ->
                                        !snapshotBuilder
                                                .checksum(snapshot.getPayload())
                                                .equals(snapshot.getChecksum()))
                        .peek(
                                snapshot ->
                                        log.error(
                                                "checksum de snapshot divergente snapshotId={}"
                                                        + " contractPeriodId={} snapshotAt={}",
                                                snapshot.getId(),
                                                snapshot.getContractPeriodId(),
                                                snapshot.getSnapshotAt()))
                        .count();

        log.info(
                "verificação de integridade de snapshots concluída verificados={} divergentes={}",
                snapshots.size(),
                invalid);
    }
}
