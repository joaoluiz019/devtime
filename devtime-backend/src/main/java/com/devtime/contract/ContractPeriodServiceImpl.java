package com.devtime.contract;

import com.devtime.contract.domain.ContractPeriod;
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
}
