package com.devtime.contract;

import com.devtime.audit.AuditService;
import com.devtime.contract.domain.BalanceExceptions;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.dto.BalanceRequests.ReopenPeriodRequest;
import com.devtime.contract.dto.BalanceResponses.ReopenPeriodResponse;
import com.devtime.contract.event.BalanceEvents.PeriodReopenedEvent;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.time.TenantClock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reabertura de período (ver {@link PeriodReopeningService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PeriodReopeningServiceImpl implements PeriodReopeningService {

    private static final String ENTITY_TYPE = "ContractPeriod";

    /** RN-242: mesma exigência de RN-215 — uma justificativa curta não justifica nada. */
    private static final int MIN_REASON_LENGTH = 10;

    private final ContractPeriodRepository periodRepository;
    private final ReopeningGuard reopeningGuard;
    private final List<PeriodWorkLogSource> workLogSources;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantClock clock;

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'PERIOD_REOPEN')")
    public ReopenPeriodResponse reopen(UUID periodId, ReopenPeriodRequest request) {
        // O lock pessimista também aqui: reabrir enquanto um fechamento está em curso produziria
        // um período CLOSED sem lockedAt nos registros.
        ContractPeriod period =
                periodRepository
                        .findByIdForUpdate(periodId)
                        .orElseThrow(
                                () -> EntityNotFoundException.of(ContractPeriod.class, periodId));

        String reason = request.reason() == null ? "" : request.reason().strip();
        if (reason.length() < MIN_REASON_LENGTH) {
            throw BalanceExceptions.justificationTooShort(reason.length()); // RN-242
        }
        reopeningGuard.assertReopenable(period); // RN-244

        Instant now = clock.now();
        int unlocked =
                workLogSources.stream().mapToInt(source -> source.unlockByPeriod(periodId)).sum();

        period.setStatus(PeriodStatus.REOPENED);
        period.setReopenCount((short) (period.getReopenCount() + 1));
        period.setReopenedAt(now);
        // closedAt e closedBy permanecem: eles documentam o fechamento que ocorreu, e limpá-los
        // apagaria a trilha da entrega anterior. O status REOPENED é o que indica a situação atual.
        //
        // INV-SNP-01: o snapshot NÃO é apagado. Um refechamento gera um segundo, versionado por
        // snapshotAt (CX-18); ambos ficam, porque cada um documenta um relatório entregue.

        // §18: a justificativa completa vai para a trilha. Sem o motivo registrado, alterar um
        // relatório já emitido é indefensável em disputa contratual.
        auditService.record(
                "PERIOD_REOPENED",
                ENTITY_TYPE,
                periodId,
                Map.of(
                        "status",
                        PeriodStatus.CLOSED.name(),
                        "reopenCount",
                        period.getReopenCount() - 1),
                Map.of(
                        "status", PeriodStatus.REOPENED.name(),
                        "reopenCount", period.getReopenCount()),
                Map.of("reason", reason, "unlockedWorkLogs", unlocked));
        events.publish(
                new PeriodReopenedEvent(periodId, period.getContractId(), period.getReopenCount()));

        // §28: WARN, e sem a justificativa no log — ela é texto livre com conteúdo comercial.
        log.warn(
                "período reaberto periodId={} reopenCount={} unlockedWorkLogs={}",
                periodId,
                period.getReopenCount(),
                unlocked);

        return new ReopenPeriodResponse(
                periodId, PeriodStatus.REOPENED.name(), period.getReopenCount(), unlocked, now);
    }
}
