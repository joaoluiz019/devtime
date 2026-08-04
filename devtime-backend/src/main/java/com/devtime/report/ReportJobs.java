package com.devtime.report;

import com.devtime.report.domain.ExportStatus;
import com.devtime.report.domain.ReportExecution;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.shared.time.TenantClock;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Jobs da feature 012 (§22.4 de specs/012).
 *
 * <p>BR-049: os dois percorrem <b>todos</b> os tenants e definem o contexto a cada item. Sem isso,
 * a geração escreveria e leria fora do tenant a que a exportação pertence — e o worker produziria
 * um arquivo com dados de outra organização.
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class ReportJobs {

    /** BR-186: lote por execução, para que uma fila acumulada não monopolize o job. */
    private static final int QUEUE_BATCH = 10;

    private static final int EXPIRY_BATCH = 200;

    private final ReportExecutionRepository repository;
    private final ExportWorker worker;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    /**
     * {@code ExportProcessorJob} — assume {@code QUEUED} e reprocessa {@code FAILED} (§22.4).
     *
     * <p>A cada 30 segundos. O intervalo é curto porque do outro lado há alguém esperando um
     * arquivo: §21.3 descreve o polling da tela em 3 segundos, e uma fila que só avançasse a cada
     * minuto tornaria esse polling um contador vazio.
     */
    @Scheduled(cron = "*/30 * * * * *")
    @SchedulerLock(name = "exportProcessor", lockAtMostFor = "PT10M")
    public void processQueue() {
        List<ReportExecution> pending =
                repository.findPendingWork(
                        List.of(ExportStatus.QUEUED, ExportStatus.FAILED),
                        ReportExecution.MAX_ATTEMPTS,
                        PageRequest.of(0, QUEUE_BATCH));

        int processed = 0;
        for (ReportExecution execution : pending) {
            if (inTenant(execution.getTenantId(), () -> worker.process(execution.getId()))) {
                processed++;
            }
        }
        if (!pending.isEmpty()) {
            log.info(
                    "fila de exportação processada itens={} concluidos={}",
                    pending.size(),
                    processed);
        }
    }

    /**
     * {@code ExportExpiryJob} — marca {@code EXPIRED} e <b>remove o binário</b> (§22.4, SG-09).
     *
     * <p>A remoção não é opcional nem adiável: um arquivo que permanece no storage depois da
     * expiração é dado pessoal fora de qualquer controle de acesso (§19.1). Marcar o registro sem
     * apagar o objeto daria a aparência de conformidade sem o efeito.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @SchedulerLock(name = "exportExpiry", lockAtMostFor = "PT30M")
    public void expireFiles() {
        List<ReportExecution> expired =
                repository.findExpired(clock.now(), PageRequest.of(0, EXPIRY_BATCH));

        int removed = 0;
        for (ReportExecution execution : expired) {
            if (inTenant(execution.getTenantId(), () -> worker.expire(execution.getId()))) {
                removed++;
            }
        }
        if (!expired.isEmpty()) {
            log.info("exportações expiradas itens={} removidas={}", expired.size(), removed);
        }
    }

    /**
     * BR-049 e BR-187: contexto do tenant do próprio item, e falha em um não interrompe os demais.
     */
    private boolean inTenant(UUID tenantId, BooleanSupplier action) {
        var previous = tenantContext.session().orElse(null);
        tenantContext.set(
                TenantSession.system(tenantId, Role.OWNER, RolePermissions.of(Role.OWNER)));
        try {
            return action.getAsBoolean();
        } catch (RuntimeException failure) {
            log.error("falha ao processar exportação do tenant tenantId={}", tenantId, failure);
            return false;
        } finally {
            if (previous == null) {
                tenantContext.clear();
            } else {
                tenantContext.set(previous);
            }
        }
    }
}
