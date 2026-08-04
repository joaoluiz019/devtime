package com.devtime.shared.maintenance;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job noturno de reconciliação de desnormalizados (specs 003, 006, 007, 008 e 011, §22.4).
 *
 * <p>Vive em {@code shared} porque não pertence a nenhuma feature: ele conhece apenas {@link
 * DenormalizationReconciler}, e são as features que registram implementações (AR-01 continua
 * valendo — a dependência aponta para a interface, nunca para a feature).
 *
 * <p><b>Cada reconciliador falha sozinho.</b> JB-04: uma divergência que faça um deles estourar não
 * pode impedir os outros quatro de rodarem naquela noite — a exceção é registrada e engolida, como
 * nos jobs por tenant de {@code 004}.
 *
 * <p>Divergência encontrada é registrada em {@code WARN} com o alvo e a quantidade: ela significa
 * que um incremento transacional se perdeu, e isso precisa ser investigado mesmo depois de o número
 * ter sido corrigido. Corrigir em silêncio esconderia o defeito que produziu a divergência.
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class DenormalizationReconcileJob {

    private final List<DenormalizationReconciler> reconcilers;

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "denormReconcile", lockAtMostFor = "PT30M")
    public void reconcile() {
        int corrected = 0;
        for (DenormalizationReconciler reconciler : reconcilers) {
            try {
                int fixed = reconciler.reconcile();
                corrected += fixed;
                if (fixed > 0) {
                    log.warn(
                            "desnormalizado divergente corrigido alvo={} registros={}",
                            reconciler.target(),
                            fixed);
                }
            } catch (RuntimeException failure) {
                log.error("falha ao reconciliar alvo={}", reconciler.target(), failure);
            }
        }
        log.info(
                "reconciliação de desnormalizados concluída reconciliadores={} corrigidos={}",
                reconcilers.size(),
                corrected);
    }
}
