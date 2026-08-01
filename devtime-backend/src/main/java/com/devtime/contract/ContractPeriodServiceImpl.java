package com.devtime.contract;

import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.dto.ContractResponses.ContractPeriodRefResponse;
import com.devtime.contract.dto.ContractResponses.ContractPeriodResponse;
import com.devtime.shared.error.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de períodos (spec 004 §22.2). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractPeriodServiceImpl implements ContractPeriodService {

    private final ContractPeriodRepository repository;
    private final ContractMapper mapper;

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public List<ContractPeriodResponse> listByContract(UUID contractId) {
        return mapper.toPeriodResponses(repository.findByContractIdOrderBySequence(contractId));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public ContractPeriodResponse getById(UUID periodId) {
        return mapper.toPeriodResponse(
                repository
                        .findById(periodId)
                        .orElseThrow(
                                () -> EntityNotFoundException.of(ContractPeriod.class, periodId)));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public Optional<ContractPeriodResponse> getCurrentPeriod(UUID contractId) {
        return repository.findOpenByContractId(contractId).map(mapper::toPeriodResponse);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public Optional<ContractPeriodResponse> resolveOpenPeriod(UUID contractId, LocalDate workDate) {
        // RN-107: o intervalo é fechado [startDate, endDate] (entities.md §7.2).
        return repository
                .findByContractIdAndDate(contractId, workDate)
                .map(mapper::toPeriodResponse);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public Optional<ContractPeriodRefResponse> resolvePeriodRef(
            UUID contractId, LocalDate workDate) {
        return repository.findByContractIdAndDate(contractId, workDate).map(this::toRef);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public ContractPeriodRefResponse getRefById(UUID periodId) {
        return toRef(
                repository
                        .findById(periodId)
                        .orElseThrow(
                                () -> EntityNotFoundException.of(ContractPeriod.class, periodId)));
    }

    /** RN-605 (ver {@link ContractPeriodService#findEndingOn}). Consulta de job; sem permissão. */
    @Override
    public List<com.devtime.contract.dto.ContractResponses.PeriodReminderView> findEndingOn(
            LocalDate endDate) {
        return repository.findOpenEndingOn(endDate).stream()
                .map(
                        period ->
                                new com.devtime.contract.dto.ContractResponses.PeriodReminderView(
                                        period.getTenantId(),
                                        period.getId(),
                                        period.getContractId(),
                                        period.getLabel(),
                                        period.getEndDate()))
                .toList();
    }

    /**
     * §4.6 de state-machines.md: {@code OPEN} e {@code REOPENED} aceitam escrita de work log.
     *
     * <p>A decisão é tomada aqui e viaja pronta na referência, para que {@code 008} não precise
     * conhecer {@code PeriodStatus} nem reimplementar a leitura da máquina de estados (AR-02).
     */
    private ContractPeriodRefResponse toRef(ContractPeriod period) {
        boolean acceptsWorkLogs =
                period.getStatus() == PeriodStatus.OPEN
                        || period.getStatus() == PeriodStatus.REOPENED;
        return new ContractPeriodRefResponse(
                period.getId(),
                period.getContractId(),
                period.getSequence(),
                period.getLabel(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus().name(),
                acceptsWorkLogs,
                period.getContractedMinutes(),
                period.getCarriedInMinutes(),
                period.getAdjustmentMinutes(),
                period.getConsumedMinutes(),
                period.getNonBillableMinutes(),
                period.getCurrency());
    }
}
