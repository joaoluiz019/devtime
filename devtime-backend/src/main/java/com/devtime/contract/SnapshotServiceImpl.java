package com.devtime.contract;

import com.devtime.contract.domain.PeriodSnapshot;
import com.devtime.contract.dto.BalanceResponses.PeriodSnapshotResponse;
import com.devtime.shared.error.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leitura e verificação de snapshots (ver {@link SnapshotService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SnapshotServiceImpl implements SnapshotService {

    private final PeriodSnapshotRepository repository;
    private final SnapshotBuilder builder;

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public Optional<PeriodSnapshotResponse> latest(UUID periodId) {
        return repository.findLatestByPeriod(periodId).map(this::toResponse);
    }

    /** Sem {@code @PreAuthorize}: consumido por {@code 012}, que já verificou {@code REPORT_*}. */
    @Override
    public Optional<String> payloadForReport(UUID periodId) {
        return repository.findLatestByPeriod(periodId).map(PeriodSnapshot::getPayload);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public boolean verifyChecksum(UUID snapshotId) {
        PeriodSnapshot snapshot =
                repository
                        .findById(snapshotId)
                        .orElseThrow(
                                () -> EntityNotFoundException.of(PeriodSnapshot.class, snapshotId));
        return isValid(snapshot);
    }

    private boolean isValid(PeriodSnapshot snapshot) {
        boolean valid = builder.checksum(snapshot.getPayload()).equals(snapshot.getChecksum());
        if (!valid) {
            // CX-21: ERROR com alerta operacional, e nenhuma correção automática. Uma divergência
            // aqui significa alteração direta no banco de um dado que deveria ser imutável.
            log.error(
                    "checksum de snapshot divergente snapshotId={} contractPeriodId={}",
                    snapshot.getId(),
                    snapshot.getContractPeriodId());
        }
        return valid;
    }

    private PeriodSnapshotResponse toResponse(PeriodSnapshot snapshot) {
        return new PeriodSnapshotResponse(
                snapshot.getId(),
                snapshot.getContractPeriodId(),
                snapshot.getSnapshotAt(),
                snapshot.getSchemaVersion(),
                snapshot.getChecksum(),
                isValid(snapshot),
                snapshot.getPayload());
    }
}
