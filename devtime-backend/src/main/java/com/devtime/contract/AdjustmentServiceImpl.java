package com.devtime.contract;

import com.devtime.audit.AuditService;
import com.devtime.contract.domain.AdjustmentReason;
import com.devtime.contract.domain.BalanceExceptions;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodAdjustment;
import com.devtime.contract.domain.PeriodBalance;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.dto.BalanceRequests.AdjustmentRequest;
import com.devtime.contract.dto.BalanceResponses.AdjustmentResponse;
import com.devtime.contract.event.BalanceEvents.AdjustmentAppliedEvent;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ajustes manuais de saldo (ver {@link AdjustmentService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AdjustmentServiceImpl implements AdjustmentService {

    private static final String ENTITY_TYPE = "PeriodAdjustment";

    /** RN-215. */
    private static final int MIN_JUSTIFICATION_LENGTH = 10;

    private final PeriodAdjustmentRepository repository;
    private final ContractPeriodRepository periodRepository;
    private final BalanceCalculator calculator;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'PERIOD_ADJUST')")
    public AdjustmentResponse apply(UUID periodId, AdjustmentRequest request) {
        return persist(
                periodId,
                request.minutes(),
                request.reason(),
                request.justification(),
                tenantContext.requireUserId(),
                false);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public List<AdjustmentResponse> listByPeriod(UUID periodId) {
        return repository.findByPeriod(periodId).stream().map(this::toResponse).toList();
    }

    /**
     * Sem {@code @PreAuthorize}: executado por job, sem usuário autenticado (CE-S-06). Nenhuma rota
     * HTTP o alcança.
     */
    @Override
    @Transactional
    public AdjustmentResponse applySystemExpiry(UUID periodId, int minutes, String justification) {
        return persist(
                periodId,
                minutes,
                AdjustmentReason.OTHER, // RN-230
                justification,
                tenantContext.currentUserId().orElse(null),
                true);
    }

    private AdjustmentResponse persist(
            UUID periodId,
            int minutes,
            AdjustmentReason reason,
            String rawJustification,
            UUID appliedBy,
            boolean systemAction) {
        ContractPeriod period = requirePeriod(periodId);

        // RN-235: ajustar um período fechado alteraria um relatório já entregue (ART-005).
        if (period.getStatus() != PeriodStatus.OPEN
                && period.getStatus() != PeriodStatus.REOPENED) {
            throw BalanceExceptions.periodNotAdjustable(period.getStatus());
        }

        String justification = rawJustification == null ? "" : rawJustification.strip();
        if (justification.length() < MIN_JUSTIFICATION_LENGTH) {
            throw BalanceExceptions.justificationTooShort(justification.length()); // RN-215
        }

        // RN-237: o disponível resultante não pode ficar negativo. CX-08: zero é permitido —
        // a regra proíbe negativo, não a exaustão do saldo.
        PeriodBalance before = calculator.calculate(period);
        if (before.availableMinutes() + minutes < 0) {
            throw BalanceExceptions.wouldMakeBalanceNegative(before.availableMinutes(), minutes);
        }

        PeriodAdjustment adjustment = new PeriodAdjustment();
        adjustment.setContractPeriodId(periodId);
        adjustment.setMinutes(minutes);
        adjustment.setReason(reason);
        adjustment.setJustification(justification);
        // SG-06: sempre do servidor, nunca da requisição.
        adjustment.setAppliedBy(appliedBy);
        adjustment.setAppliedAt(clock.now());
        PeriodAdjustment saved = repository.save(adjustment);

        // O desnormalizado é ajustado por incremento, como consumedMinutes: a soma real está nos
        // ajustes e é reconciliada no fechamento.
        periodRepository.adjustAdjustmentMinutes(periodId, minutes);

        Map<String, Object> beforeState =
                Map.of(
                        "adjustmentMinutes", before.adjustmentMinutes(),
                        "availableMinutes", before.availableMinutes());
        Map<String, Object> afterState =
                Map.of(
                        "adjustmentMinutes", before.adjustmentMinutes() + minutes,
                        "availableMinutes", before.availableMinutes() + minutes);
        // §18: reason e justification vão para a trilha, nunca para o log da aplicação.
        Map<String, Object> metadata =
                Map.of("reason", reason.name(), "justification", justification);
        if (systemAction) {
            auditService.recordSystemAction(
                    "PERIOD_ADJUSTMENT_APPLIED",
                    ENTITY_TYPE,
                    saved.getId(),
                    beforeState,
                    afterState,
                    metadata);
        } else {
            auditService.record(
                    "PERIOD_ADJUSTMENT_APPLIED",
                    ENTITY_TYPE,
                    saved.getId(),
                    beforeState,
                    afterState,
                    metadata);
        }

        // RN-602: um ajuste altera o disponível e pode cruzar um limiar de consumo.
        events.publish(
                new AdjustmentAppliedEvent(
                        saved.getId(), periodId, period.getContractId(), minutes));
        log.info(
                "ajuste de período aplicado adjustmentId={} periodId={} minutes={} reason={}",
                saved.getId(),
                periodId,
                minutes,
                reason);
        return toResponse(saved);
    }

    private ContractPeriod requirePeriod(UUID periodId) {
        return periodRepository
                .findById(periodId)
                .orElseThrow(() -> EntityNotFoundException.of(ContractPeriod.class, periodId));
    }

    private AdjustmentResponse toResponse(PeriodAdjustment adjustment) {
        return new AdjustmentResponse(
                adjustment.getId(),
                adjustment.getContractPeriodId(),
                adjustment.getMinutes(),
                adjustment.getReason().name(),
                adjustment.getJustification(),
                adjustment.getAppliedBy(),
                adjustment.getAppliedAt());
    }
}
