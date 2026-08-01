package com.devtime.contract;

import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.shared.persistence.SoftDeleteRepository;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Incremento atômico do consumo (RN-219, RN-223).
     *
     * <p>CP-15: reagregar todos os work logs do período a cada registro seria linear no volume, no
     * caminho mais quente do sistema. Ler-modificar-escrever perderia atualizações quando duas
     * pessoas registram horas no mesmo período ao mesmo tempo — o caso normal, não a exceção.
     */
    @Modifying
    @Query(
            """
            UPDATE ContractPeriod p
               SET p.consumedMinutes = p.consumedMinutes + :billableDelta,
                   p.nonBillableMinutes = p.nonBillableMinutes + :nonBillableDelta
             WHERE p.id = :id
            """)
    int adjustConsumption(
            @Param("id") UUID id,
            @Param("billableDelta") int billableDelta,
            @Param("nonBillableDelta") int nonBillableDelta);

    /** RN-237: soma dos ajustes é mantida desnormalizada em {@code adjustmentMinutes}. */
    @Modifying
    @Query(
            """
            UPDATE ContractPeriod p
               SET p.adjustmentMinutes = p.adjustmentMinutes + :delta
             WHERE p.id = :id
            """)
    int adjustAdjustmentMinutes(@Param("id") UUID id, @Param("delta") int delta);

    /**
     * RN-241 passo 0: lock pessimista antes de iniciar o fechamento.
     *
     * <p>CE-ME-08: com <i>optimistic locking</i>, dois fechamentos simultâneos executariam os sete
     * passos e um falharia no commit — mas o passo 3 já teria travado work logs e o passo 4 já
     * teria gerado um snapshot. O lock pessimista impede o segundo de começar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ContractPeriod p WHERE p.id = :id")
    Optional<ContractPeriod> findByIdForUpdate(@Param("id") UUID id);

    /**
     * RN-244: existe algum período posterior já fechado? A reabertura vai do mais recente ao mais
     * antigo.
     */
    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.contractId = :contractId
               AND p.sequence > :sequence
               AND p.status = com.devtime.contract.domain.PeriodStatus.CLOSED
             ORDER BY p.sequence ASC
            """)
    List<ContractPeriod> findClosedAfter(
            @Param("contractId") UUID contractId, @Param("sequence") int sequence);

    /** RN-229: período seguinte, que recebe o {@code carriedOut} deste. */
    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.contractId = :contractId
               AND p.sequence = :sequence
            """)
    Optional<ContractPeriod> findByContractIdAndSequence(
            @Param("contractId") UUID contractId, @Param("sequence") int sequence);

    /**
     * CE-ME-07: períodos presos em {@code CLOSING} além do limite.
     *
     * <p>Consulta de job de plataforma: percorre todos os tenants, e o filtro é reaplicado a cada
     * iteração pelo contexto (BR-049). O período preso bloqueia toda escrita naquele ciclo, então
     * detectá-lo rápido importa mais do que o custo da varredura.
     */
    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.status = com.devtime.contract.domain.PeriodStatus.CLOSING
               AND p.updatedAt < :threshold
            """)
    List<ContractPeriod> findStuckClosing(@Param("threshold") java.time.Instant threshold);
}
