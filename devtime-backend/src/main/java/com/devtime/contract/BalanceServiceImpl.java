package com.devtime.contract;

import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodBalance;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.dto.BalanceResponses.OverageCheckResponse;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.event.BalanceEvents.ConsumptionChangedEvent;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fórmulas canônicas e consumo do período (ver {@link BalanceService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BalanceServiceImpl implements BalanceService {

    private final ContractPeriodRepository periodRepository;
    private final ContractRepository contractRepository;
    private final BalanceCalculator calculator;
    private final DomainEventPublisher events;
    private final EntityManager entityManager;

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public PeriodBalanceResponse getBalance(UUID periodId) {
        return toResponse(requirePeriod(periodId));
    }

    /**
     * Sem {@code @PreAuthorize}: é chamado de dentro da transação de {@code 008}, que já verificou
     * {@code WORKLOG_CREATE}/{@code WORKLOG_UPDATE_*}/{@code WORKLOG_DELETE_*}. Exigir {@code
     * PERIOD_VIEW} aqui recusaria o registro de horas a papéis que podem registrá-las. Nenhuma rota
     * HTTP alcança este método, e é essa ausência que impede manipular o consumo pela API (SG-06).
     */
    @Override
    @Transactional
    public void applyConsumptionDelta(UUID periodId, int billableDelta, int nonBillableDelta) {
        if (billableDelta == 0 && nonBillableDelta == 0) {
            return;
        }
        // ART-024: período de outro tenant é inexistente. Sem esta leitura, o UPDATE filtrado não
        // afetaria linha alguma e o consumo se perderia em silêncio.
        ContractPeriod period = requirePeriod(periodId);
        periodRepository.adjustConsumption(period.getId(), billableDelta, nonBillableDelta);
        // O UPDATE em massa não atualiza a instância gerenciada: sem o refresh, qualquer leitura
        // seguinte na mesma transação — inclusive o saldo devolvido na resposta de `008` — enxerga
        // o valor anterior ao próprio lançamento que acabou de ser feito. Quem registra horas via
        // o saldo sem elas.
        entityManager.refresh(period);

        // RN-602: os limiares são reavaliados a cada alteração de consumo. O evento é publicado
        // aqui, e não em 008, porque é este ponto que conhece o valor resultante.
        events.publish(
                new ConsumptionChangedEvent(period.getId(), period.getContractId(), billableDelta));
        log.debug(
                "consumo do período ajustado periodId={} billableDelta={} nonBillableDelta={}",
                periodId,
                billableDelta,
                nonBillableDelta);
    }

    /** Sem {@code @PreAuthorize} pelo mesmo motivo de {@link #applyConsumptionDelta}. */
    @Override
    public OverageCheckResponse checkOverage(UUID periodId, int additionalBillableMinutes) {
        ContractPeriod period = requirePeriod(periodId);
        Contract contract = requireContract(period.getContractId());
        PeriodBalance balance = calculator.calculate(period);

        int projectedConsumption = balance.consumedMinutes() + additionalBillableMinutes;
        int exceeding = Math.max(0, projectedConsumption - balance.availableMinutes());
        // CX-23 / RN-210: contrato HOURLY_OPEN não tem teto por definição — available é 0 e
        // qualquer registro "excederia". Tratá-lo como excedente bloquearia todo o modelo de horas
        // abertas, então ele nunca excede.
        boolean hourlyOpen = balance.availableMinutes() == 0 && period.getContractedMinutes() == 0;

        return new OverageCheckResponse(
                period.getId(),
                contract.getOveragePolicy().name(),
                exceeding > 0 && !hourlyOpen,
                balance.availableMinutes(),
                balance.consumedMinutes(),
                balance.remainingMinutes(),
                hourlyOpen ? 0 : exceeding);
    }

    /** Compartilhado com o extrato e o fechamento; mantém uma única montagem da resposta. */
    PeriodBalanceResponse toResponse(ContractPeriod period) {
        PeriodBalance balance = calculator.calculate(period);
        boolean partial =
                period.getStatus() == PeriodStatus.OPEN
                        || period.getStatus() == PeriodStatus.REOPENED;
        return new PeriodBalanceResponse(
                period.getId(),
                period.getContractId(),
                period.getSequence(),
                period.getLabel(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus().name(),
                balance.contractedMinutes(),
                balance.carriedInMinutes(),
                balance.adjustmentMinutes(),
                balance.availableMinutes(),
                balance.consumedMinutes(),
                balance.nonBillableMinutes(),
                balance.remainingMinutes(),
                balance.overageMinutes(),
                balance.consumptionRate(),
                partial,
                period.getReopenCount(),
                period.getCurrency());
    }

    ContractPeriod requirePeriod(UUID periodId) {
        return periodRepository
                .findById(periodId)
                .orElseThrow(() -> EntityNotFoundException.of(ContractPeriod.class, periodId));
    }

    private Contract requireContract(UUID contractId) {
        return contractRepository
                .findById(contractId)
                .orElseThrow(() -> EntityNotFoundException.of(Contract.class, contractId));
    }
}
