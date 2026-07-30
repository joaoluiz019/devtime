package com.devtime.contract;

import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.shared.persistence.SoftDeleteRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistência de {@link ContractPeriod} (spec 004 §25). */
@Repository
public interface ContractPeriodRepository extends SoftDeleteRepository<ContractPeriod> {

    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.contractId = :contractId
             ORDER BY p.sequence ASC
            """)
    List<ContractPeriod> findByContractIdOrderBySequence(@Param("contractId") UUID contractId);

    /** INV-PER-07: no máximo um período aberto por contrato. */
    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.contractId = :contractId
               AND p.status = com.devtime.contract.domain.PeriodStatus.OPEN
            """)
    Optional<ContractPeriod> findOpenByContractId(@Param("contractId") UUID contractId);

    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.contractId = :contractId
             ORDER BY p.sequence DESC
             LIMIT 1
            """)
    Optional<ContractPeriod> findLastByContractId(@Param("contractId") UUID contractId);

    /**
     * RN-107: período cujo intervalo fechado {@code [startDate, endDate]} contém a data.
     *
     * <p>Interface consumida por {@code 008-worklogs} para resolver {@code contractPeriodId}.
     */
    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.contractId = :contractId
               AND p.startDate <= :workDate
               AND p.endDate >= :workDate
            """)
    Optional<ContractPeriod> findByContractIdAndDate(
            @Param("contractId") UUID contractId, @Param("workDate") LocalDate workDate);

    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.contractId = :contractId
               AND p.status IN :statuses
             ORDER BY p.sequence ASC
            """)
    List<ContractPeriod> findByContractIdAndStatusIn(
            @Param("contractId") UUID contractId, @Param("statuses") List<PeriodStatus> statuses);
}
