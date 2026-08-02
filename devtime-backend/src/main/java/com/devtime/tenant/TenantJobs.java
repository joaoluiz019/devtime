package com.devtime.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Jobs de organização e vínculo (spec 002 §22.4).
 *
 * <p>BR-184/BR-185: bloqueio distribuído e idempotência por predicado — ambos operam sobre o estado
 * atual, então reexecutar não produz efeito diferente. Ativos apenas no perfil {@code scheduler}
 * (backend.md §13.1).
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class TenantJobs {

    private final TenantService tenantService;
    private final InvitationService invitationService;

    /**
     * RN-008: purga organizações canceladas há mais de 30 dias.
     *
     * <p>Nível {@code WARN} mesmo em execução normal: a purga é a única operação do sistema que
     * torna dados inalcançáveis, e a ausência de rastro dela no log seria um problema em auditoria.
     */
    @Scheduled(cron = "0 30 4 * * *")
    @SchedulerLock(name = "tenantPurge", lockAtMostFor = "PT30M")
    public void purgeCancelledTenants() {
        int purged = tenantService.purgeExpiredCancellations();
        if (purged > 0) {
            log.warn("organizações purgadas após a retenção quantidade={}", purged);
        }
    }

    /** RN-457: {@code INVITED → REMOVED} após 7 dias. */
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "expiredInvitations", lockAtMostFor = "PT10M")
    public void expireInvitations() {
        int expired = invitationService.expirePending();
        if (expired > 0) {
            log.info("convites expirados quantidade={}", expired);
        }
    }
}
