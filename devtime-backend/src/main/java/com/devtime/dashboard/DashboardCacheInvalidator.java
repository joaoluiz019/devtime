package com.devtime.dashboard;

import com.devtime.contract.event.BalanceEvents.AdjustmentAppliedEvent;
import com.devtime.contract.event.BalanceEvents.PeriodClosedEvent;
import com.devtime.contract.event.BalanceEvents.PeriodReopenedEvent;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.timer.event.TimerEvents.TimerCompletedEvent;
import com.devtime.worklog.event.WorkLogEvents.WorkLogCreatedEvent;
import com.devtime.worklog.event.WorkLogEvents.WorkLogDeletedEvent;
import com.devtime.worklog.event.WorkLogEvents.WorkLogUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Invalida o cache de gráficos quando o dado de origem muda (§15 de specs/010).
 *
 * <p><b>Esta feature não publica evento algum</b> — apenas consome. Um painel que publicasse
 * eventos criaria dependência circular com as seis features que ele agrega.
 *
 * <p>BR-128 / BR-183: {@code AFTER_COMMIT}. Invalidar dentro da transação descartaria o cache de
 * uma alteração que ainda pode ser desfeita, e a recarga seguinte poderia repovoá-lo com o estado
 * anterior ao rollback — deixando o cache mais errado do que antes.
 *
 * <p>ER-08: a invalidação é efeito não essencial. A falha é registrada e engolida; o pior caso é um
 * gráfico até cinco minutos desatualizado, contra derrubar o registro de horas que a originou.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardCacheInvalidator {

    private final DashboardChartCache cache;
    private final TenantContext tenantContext;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkLogCreated(WorkLogCreatedEvent event) {
        invalidate("WorkLogCreated");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkLogUpdated(WorkLogUpdatedEvent event) {
        invalidate("WorkLogUpdated");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkLogDeleted(WorkLogDeletedEvent event) {
        invalidate("WorkLogDeleted");
    }

    /** O encerramento do cronômetro gera um work log, que altera todas as agregações. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimerCompleted(TimerCompletedEvent event) {
        invalidate("TimerCompleted");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPeriodClosed(PeriodClosedEvent event) {
        invalidate("PeriodClosed");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPeriodReopened(PeriodReopenedEvent event) {
        invalidate("PeriodReopened");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdjustmentApplied(AdjustmentAppliedEvent event) {
        invalidate("AdjustmentApplied");
    }

    private void invalidate(String cause) {
        tenantContext
                .currentTenantId()
                .ifPresentOrElse(
                        tenantId -> {
                            cache.invalidateTenant(tenantId);
                            cache.evictExpired();
                            log.debug("cache de gráficos invalidado causa={}", cause);
                        },
                        // O evento sempre nasce dentro de uma requisição ou de uma iteração de job
                        // com contexto definido (BR-049). Ausência é defeito, não caso de uso: sem
                        // tenant não há o que invalidar sem afetar os demais.
                        () ->
                                log.warn(
                                        "invalidação de cache sem tenant no contexto causa={}",
                                        cause));
    }
}
