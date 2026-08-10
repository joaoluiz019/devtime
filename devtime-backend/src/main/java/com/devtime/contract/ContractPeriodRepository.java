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
    // `flushAutomatically`: o UPDATE em massa vai direto ao banco. Sem descarregar antes, uma
    // alteração pendente na mesma entidade seria escrita **depois** do incremento e o
    // sobrescreveria
    // com o valor lido no início da transação.
    @Modifying(flushAutomatically = true)
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
    @Modifying(flushAutomatically = true)
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
     * Varredura de plataforma por situação, para a reconciliação noturna (spec 011 §22.4).
     *
     * <p>Sem sessão o filtro de tenant não é aplicado, como em {@link #findStuckClosing} — é o
     * mecanismo previsto por BR-049 para job que percorre todas as organizações.
     */
    @Query("SELECT p FROM ContractPeriod p WHERE p.status IN :statuses")
    List<ContractPeriod> findByStatusIn(@Param("statuses") List<PeriodStatus> statuses);

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

    /**
     * RN-605: períodos abertos cujo término é exatamente a data informada.
     *
     * <p>Restrita a {@code OPEN} e {@code REOPENED} — avisar sobre o fechamento iminente de um
     * período já fechado não teria sentido. Consulta de job de plataforma, como {@link
     * #findStuckClosing}.
     */
    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.endDate = :endDate
               AND p.status IN (com.devtime.contract.domain.PeriodStatus.OPEN,
                                com.devtime.contract.domain.PeriodStatus.REOPENED)
            """)
    List<ContractPeriod> findOpenEndingOn(@Param("endDate") LocalDate endDate);

    /**
     * CX-12 de {@code specs/002-users}: existe período em {@code CLOSING} no tenant da sessão?
     *
     * <p>Tenant-scoped pelo filtro automático (ART-022), ao contrário de {@link #findStuckClosing},
     * que é varredura de plataforma. {@code EXISTS} porque a pergunta é binária — carregar os
     * períodos para contá-los seria trabalho descartado.
     */
    @Query(
            """
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM ContractPeriod p
             WHERE p.status = com.devtime.contract.domain.PeriodStatus.CLOSING
            """)
    boolean existsClosingInTenant();

    /**
     * RN-213: períodos abertos cujo fim está a {@code ≤ 3} dias e que ainda não têm sucessor.
     *
     * <p>Varredura de plataforma, como {@link #findOpenEndingOn}. Três condições fazem o trabalho e
     * nenhuma delas é dispensável:
     *
     * <ul>
     *   <li>o contrato precisa estar {@code ACTIVE} — um contrato suspenso interrompe a geração
     *       (FA-03), e um encerrado não deve receber período novo (RN-214);
     *   <li>{@code autoRenew} precisa estar ligado — é o que o usuário controla;
     *   <li><b>não pode existir sucessor</b>. Sem esse {@code NOT EXISTS}, uma segunda execução no
     *       mesmo dia criaria o período de novo. O índice único {@code (contract_id, sequence)} é a
     *       segunda barreira, não a primeira (R-04).
     * </ul>
     */
    @Query(
            """
            SELECT p FROM ContractPeriod p, Contract c
             WHERE c.id = p.contractId
               AND c.status = com.devtime.contract.domain.ContractStatus.ACTIVE
               AND c.autoRenew = true
               AND p.status = com.devtime.contract.domain.PeriodStatus.OPEN
               AND p.endDate <= :threshold
               AND NOT EXISTS (
                     SELECT 1 FROM ContractPeriod n
                      WHERE n.contractId = p.contractId
                        AND n.sequence > p.sequence)
             ORDER BY p.endDate ASC
            """)
    List<ContractPeriod> findRenewalDue(
            @Param("threshold") LocalDate threshold,
            org.springframework.data.domain.Pageable pageable);

    /**
     * §22.4: períodos {@code SCHEDULED} cujo {@code startDate} já chegou.
     *
     * <p>{@code <=} e não {@code =}: se o job falhar por um dia, o período precisa abrir na
     * execução seguinte. Uma comparação por igualdade deixaria o contrato sem período aberto até
     * intervenção manual.
     */
    @Query(
            """
            SELECT p FROM ContractPeriod p
             WHERE p.status = com.devtime.contract.domain.PeriodStatus.SCHEDULED
               AND p.startDate <= :reference
             ORDER BY p.startDate ASC
            """)
    List<ContractPeriod> findScheduledDue(
            @Param("reference") LocalDate reference,
            org.springframework.data.domain.Pageable pageable);

    /**
     * RN-230: candidatos à expiração de saldo transportado.
     *
     * <p>Restrita a {@code OPEN} e {@code REOPENED} por CX-19 — debitar um período fechado
     * alteraria um valor congelado (ART-005). {@code rolloverExpiryPeriods = 0} nunca expira
     * (CX-20) e por isso nem entra na varredura. Quanto expira, e se algo expira, é decisão de
     * {@link RolloverExpiryPolicy}; esta consulta apenas descarta quem não pode ter nada a expirar.
     */
    @Query(
            """
            SELECT p FROM ContractPeriod p, Contract c
             WHERE c.id = p.contractId
               AND c.rolloverExpiryPeriods > 0
               AND p.carriedInMinutes > 0
               AND p.status IN (com.devtime.contract.domain.PeriodStatus.OPEN,
                                com.devtime.contract.domain.PeriodStatus.REOPENED)
             ORDER BY p.startDate ASC
            """)
    List<ContractPeriod> findRolloverExpiryDue(org.springframework.data.domain.Pageable pageable);

    /**
     * CE-ME-02 / CX-13: períodos ainda abertos de contratos já encerrados.
     *
     * <p>O contrato {@code ENDED} teve seu período truncado por {@code 004} (RN-214); passados três
     * dias do fim, ninguém mais vai lançar horas nele e mantê-lo aberto só adia indefinidamente o
     * documento que o cliente espera. A folga existe para o lançamento retroativo legítimo do
     * último dia de trabalho.
     */
    @Query(
            """
            SELECT p FROM ContractPeriod p, Contract c
             WHERE c.id = p.contractId
               AND c.status = com.devtime.contract.domain.ContractStatus.ENDED
               AND p.endDate <= :threshold
               AND p.status IN (com.devtime.contract.domain.PeriodStatus.OPEN,
                                com.devtime.contract.domain.PeriodStatus.REOPENED)
             ORDER BY p.endDate ASC
            """)
    List<ContractPeriod> findAutoCloseDue(
            @Param("threshold") LocalDate threshold,
            org.springframework.data.domain.Pageable pageable);
}
