package com.devtime.contract;

import com.devtime.audit.AuditService;
import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.dto.BalanceRequests.ClosePeriodRequest;
import com.devtime.contract.dto.ContractRequests.ContractTransitionRequest;
import com.devtime.contract.dto.ContractResponses.MaintenanceTarget;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link ContractMaintenanceService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ContractMaintenanceServiceImpl implements ContractMaintenanceService {

    /** Justificativa registrada na trilha do encerramento automático. */
    static final String AUTO_END_REASON = "Encerramento automático ao atingir a data de fim";

    private final ContractRepository contractRepository;
    private final ContractPeriodRepository periodRepository;
    private final ContractService contractService;
    private final PeriodMaterializer periodMaterializer;
    private final PeriodAdjustmentRepository adjustmentRepository;
    private final AdjustmentService adjustmentService;
    private final PeriodClosingService closingService;
    private final RolloverExpiryPolicy rolloverExpiryPolicy;
    private final BalanceCalculator balanceCalculator;
    private final AuditService auditService;

    @Override
    public List<MaintenanceTarget> findRenewalDue(LocalDate reference, int daysAhead, int limit) {
        return periodRepository
                .findRenewalDue(reference.plusDays(daysAhead), PageRequest.of(0, limit))
                .stream()
                .map(ContractMaintenanceServiceImpl::targetOf)
                .toList();
    }

    @Override
    public List<MaintenanceTarget> findScheduledDue(LocalDate reference, int limit) {
        return periodRepository.findScheduledDue(reference, PageRequest.of(0, limit)).stream()
                .map(ContractMaintenanceServiceImpl::targetOf)
                .toList();
    }

    @Override
    public List<MaintenanceTarget> findEndDue(LocalDate reference, int limit) {
        return contractRepository.findActiveEndedBy(reference, PageRequest.of(0, limit)).stream()
                .map(
                        contract ->
                                new MaintenanceTarget(
                                        contract.getTenantId(), contract.getId(), contract.getId()))
                .toList();
    }

    @Override
    public List<MaintenanceTarget> findRolloverExpiryDue(int limit) {
        return periodRepository.findRolloverExpiryDue(PageRequest.of(0, limit)).stream()
                .map(ContractMaintenanceServiceImpl::targetOf)
                .toList();
    }

    @Override
    public List<MaintenanceTarget> findAutoCloseDue(LocalDate reference, int graceDays, int limit) {
        return periodRepository
                .findAutoCloseDue(reference.minusDays(graceDays), PageRequest.of(0, limit))
                .stream()
                .map(ContractMaintenanceServiceImpl::targetOf)
                .toList();
    }

    /**
     * RN-230 (ver {@link ContractMaintenanceService#expireRollover}).
     *
     * <p>A convergência vem da verificação de que já existe o ajuste de expiração <b>neste</b>
     * período: reexecutar o job no mesmo dia, ou em duas réplicas, não debita duas vezes. É a
     * {@code dedupeKey} por período que §22.4 exige, expressa como consulta em vez de coluna nova.
     */
    @Override
    @Transactional
    public boolean expireRollover(UUID periodId) {
        ContractPeriod period = periodRepository.findById(periodId).orElse(null);
        if (period == null
                || (period.getStatus() != PeriodStatus.OPEN
                        && period.getStatus() != PeriodStatus.REOPENED)) {
            // CX-19: entre a varredura e esta transação o período pode ter fechado.
            return false;
        }
        if (adjustmentRepository.existsSystemExpiry(periodId, RolloverExpiryPolicy.JUSTIFICATION)) {
            return false;
        }
        Contract contract = contractRepository.findById(period.getContractId()).orElse(null);
        if (contract == null || contract.getRolloverExpiryPeriods() <= 0) {
            return false; // CX-20
        }

        int grantMinutes =
                periodRepository
                        .findByContractIdAndSequence(
                                period.getContractId(),
                                period.getSequence() - contract.getRolloverExpiryPeriods())
                        .map(ContractPeriod::getCarriedInMinutes)
                        .orElse(0);
        int expiring =
                rolloverExpiryPolicy.expiringMinutes(
                        period, balanceCalculator.calculate(period), grantMinutes);
        if (expiring <= 0) {
            return false;
        }

        adjustmentService.applySystemExpiry(
                periodId, -expiring, RolloverExpiryPolicy.JUSTIFICATION);
        auditService.recordSystemAction(
                "PERIOD_ROLLOVER_EXPIRED",
                "ContractPeriod",
                periodId,
                Map.of("carriedInMinutes", period.getCarriedInMinutes()),
                Map.of("adjustmentMinutes", -expiring),
                Map.of("rolloverExpiryPeriods", (int) contract.getRolloverExpiryPeriods()));
        log.info(
                "saldo transportado expirado periodId={} minutos={}",
                periodId,
                expiring); // §26: INFO
        return true;
    }

    /** CE-ME-02 (ver {@link ContractMaintenanceService#autoClosePeriod}). */
    @Override
    @Transactional
    public boolean autoClosePeriod(UUID periodId) {
        ContractPeriod period = periodRepository.findById(periodId).orElse(null);
        if (period == null
                || (period.getStatus() != PeriodStatus.OPEN
                        && period.getStatus() != PeriodStatus.REOPENED)) {
            return false;
        }
        // `confirmed = true`: a data de fim já passou há três dias, então RN-239 não se aplica —
        // não há fechamento antecipado a confirmar. `earlyClosingReason` nulo pelo mesmo motivo.
        closingService.close(periodId, new ClosePeriodRequest(true, null));
        log.info(
                "período fechado automaticamente periodId={} contractId={}",
                periodId,
                period.getContractId());
        return true;
    }

    /**
     * A projeção é feita aqui, e não em JPQL.
     *
     * <p>Hibernate 6 não resolve o nome de uma classe aninhada em {@code SELECT new}: exigiria a
     * forma binária com {@code $}, que quebraria silenciosamente em qualquer renomeação. Projetar
     * no serviço mantém o identificador verificado pelo compilador.
     */
    private static MaintenanceTarget targetOf(ContractPeriod period) {
        return new MaintenanceTarget(period.getTenantId(), period.getId(), period.getContractId());
    }

    @Override
    @Transactional
    public boolean renewPeriod(UUID periodId) {
        Optional<ContractPeriod> current = periodRepository.findById(periodId);
        if (current.isEmpty() || current.get().getStatus() != PeriodStatus.OPEN) {
            // O período pode ter sido fechado entre a varredura e a execução. Não é falha: o
            // fechamento já cria o sucessor quando ele falta.
            return false;
        }
        ContractPeriod period = current.get();

        // BR-185: a segunda barreira contra duplicata. A varredura já exclui períodos com sucessor,
        // mas entre a leitura e a escrita outra réplica pode ter criado o dele — e o índice único
        // (contract_id, sequence) transformaria isso em erro de constraint no meio do lote.
        if (periodRepository
                .findByContractIdAndSequence(period.getContractId(), period.getSequence() + 1)
                .isPresent()) {
            return false;
        }

        Contract contract =
                contractRepository
                        .findById(period.getContractId())
                        .orElseThrow(
                                () ->
                                        com.devtime.shared.error.EntityNotFoundException.of(
                                                Contract.class, period.getContractId()));

        // RN-214: nada é gerado além do fim da vigência. O materializador já para sozinho, e a
        // lista vazia é o caso normal do último ciclo de um contrato com prazo.
        List<ContractPeriod> created =
                periodMaterializer.materialize(
                        contract,
                        period.getEndDate(),
                        period.getSequence(),
                        1,
                        PeriodStatus.SCHEDULED);
        if (created.isEmpty()) {
            return false;
        }
        log.info(
                "período renovado automaticamente contractId={} sequence={} startDate={}",
                contract.getId(),
                created.get(0).getSequence(),
                created.get(0).getStartDate());
        return true;
    }

    @Override
    @Transactional
    public boolean openScheduledPeriod(UUID periodId) {
        Optional<ContractPeriod> found = periodRepository.findById(periodId);
        if (found.isEmpty() || found.get().getStatus() != PeriodStatus.SCHEDULED) {
            return false; // Convergente: já aberto por outra execução (BR-185).
        }
        ContractPeriod period = found.get();

        // §11: a transição SCHEDULED → OPEN exige o período anterior CLOSED ou inexistente. Não é
        // formalidade — o índice único uq_periods_single_open admite um único período aberto por
        // contrato, e abrir antes do fechamento do anterior falharia no commit. Adiar é o
        // comportamento correto: o ciclo anterior ainda está recebendo horas, e o fechamento é
        // quem decide quando ele termina.
        Optional<ContractPeriod> previous =
                periodRepository.findByContractIdAndSequence(
                        period.getContractId(), period.getSequence() - 1);
        if (previous.isPresent() && previous.get().getStatus() != PeriodStatus.CLOSED) {
            log.info(
                    "abertura adiada: período anterior ainda não fechado periodId={} status={}",
                    period.getId(),
                    previous.get().getStatus());
            return false;
        }

        period.setStatus(PeriodStatus.OPEN);

        auditService.recordSystemAction(
                "PERIOD_OPENED",
                "ContractPeriod",
                period.getId(),
                Map.of("status", PeriodStatus.SCHEDULED.name()),
                Map.of("status", PeriodStatus.OPEN.name()),
                Map.of("startDate", period.getStartDate().toString()));
        log.info(
                "período aberto automaticamente periodId={} contractId={} startDate={}",
                period.getId(),
                period.getContractId(),
                period.getStartDate());
        return true;
    }

    /**
     * FA-05: delega a {@link ContractService#end}, em vez de reimplementar a transição.
     *
     * <p>O encerramento trunca o período corrente (RN-214), decrementa {@code activeContractsCount}
     * e registra a trilha. Duplicar isso aqui criaria dois encerramentos com efeitos possivelmente
     * divergentes — e o encerramento automático precisa ser indistinguível do manual, porque é o
     * mesmo fato de negócio.
     *
     * <p>A {@code endDate} atual é reenviada de propósito: {@code end} usa {@code hoje} quando ela
     * é omitida, e sobrescrever a data pactuada pela data em que o job rodou moveria o fim do
     * contrato sempre que a execução atrasasse.
     */
    @Override
    @Transactional
    public boolean endContract(UUID contractId) {
        Optional<Contract> found = contractRepository.findById(contractId);
        if (found.isEmpty() || found.get().getStatus() != ContractStatus.ACTIVE) {
            return false;
        }
        Contract contract = found.get();
        contractService.end(
                contractId,
                new ContractTransitionRequest(AUTO_END_REASON, contract.getEndDate(), null));
        log.info(
                "contrato encerrado automaticamente contractId={} code={} endDate={}",
                contractId,
                contract.getCode(),
                contract.getEndDate());
        return true;
    }
}
