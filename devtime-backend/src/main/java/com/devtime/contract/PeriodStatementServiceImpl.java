package com.devtime.contract;

import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodAdjustment;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.dto.BalanceResponses.PeriodStatementEntry;
import com.devtime.contract.dto.BalanceResponses.PeriodStatementResponse;
import com.devtime.contract.dto.BalanceResponses.PeriodWorkLogEntry;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.time.TenantClock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Extrato explicativo do período (ver {@link PeriodStatementService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeriodStatementServiceImpl implements PeriodStatementService {

    private static final String TYPE_CONTRACTED = "CONTRACTED";
    private static final String TYPE_CARRIED_IN = "CARRIED_IN";
    private static final String TYPE_ADJUSTMENT = "ADJUSTMENT";
    private static final String TYPE_WORK_LOG = "WORK_LOG";

    private final ContractPeriodRepository periodRepository;
    private final PeriodAdjustmentRepository adjustmentRepository;
    private final BalanceServiceImpl balanceService;
    private final List<PeriodWorkLogSource> workLogSources;
    private final TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public PeriodStatementResponse statement(UUID periodId) {
        ContractPeriod period =
                periodRepository
                        .findById(periodId)
                        .orElseThrow(
                                () -> EntityNotFoundException.of(ContractPeriod.class, periodId));

        List<PeriodStatementEntry> entries = new ArrayList<>();
        int running = 0;

        // Créditos de origem primeiro: são o ponto de partida do saldo, e listá-los junto com os
        // movimentos por data faria o acumulado começar do lugar errado.
        running += period.getContractedMinutes();
        entries.add(
                new PeriodStatementEntry(
                        TYPE_CONTRACTED,
                        period.getId(),
                        period.getStartDate(),
                        "Horas contratadas do período",
                        period.getContractedMinutes(),
                        running));

        if (period.getCarriedInMinutes() != 0) {
            running += period.getCarriedInMinutes();
            entries.add(
                    new PeriodStatementEntry(
                            TYPE_CARRIED_IN,
                            period.getId(),
                            period.getStartDate(),
                            "Saldo transportado do período anterior",
                            period.getCarriedInMinutes(),
                            running));
        }

        // Ajustes e registros de horas, entrelaçados por data. É o que permite ao cliente apontar
        // exatamente em que dia o saldo virou negativo.
        List<PeriodStatementEntry> movements = new ArrayList<>();
        for (PeriodAdjustment adjustment : adjustmentRepository.findByPeriod(periodId)) {
            movements.add(
                    new PeriodStatementEntry(
                            TYPE_ADJUSTMENT,
                            adjustment.getId(),
                            clock.toTenantDate(adjustment.getAppliedAt()),
                            adjustment.getReason().name() + " — " + adjustment.getJustification(),
                            adjustment.getMinutes(),
                            0));
        }
        for (PeriodWorkLogEntry workLog : workLogEntries(periodId)) {
            // RN-223: horas não faturáveis aparecem no extrato com minutos zero de consumo —
            // o trabalho existiu e é visível, mas não consumiu saldo.
            movements.add(
                    new PeriodStatementEntry(
                            TYPE_WORK_LOG,
                            workLog.id(),
                            workLog.workDate(),
                            workLog.ticketKey() + " — " + workLog.categoryName(),
                            -workLog.billableMinutes(),
                            0));
        }
        movements.sort(Comparator.comparing(PeriodStatementEntry::date));

        for (PeriodStatementEntry movement : movements) {
            running += movement.minutes();
            entries.add(
                    new PeriodStatementEntry(
                            movement.type(),
                            movement.referenceId(),
                            movement.date(),
                            movement.description(),
                            movement.minutes(),
                            running));
        }

        PeriodBalanceResponse balance = balanceService.toResponse(period);
        return new PeriodStatementResponse(periodId, balance, List.copyOf(entries));
    }

    private List<PeriodWorkLogEntry> workLogEntries(UUID periodId) {
        return workLogSources.stream()
                .flatMap(source -> source.findByPeriod(periodId).stream())
                .toList();
    }
}
