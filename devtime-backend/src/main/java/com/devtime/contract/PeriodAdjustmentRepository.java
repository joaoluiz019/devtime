package com.devtime.contract;

import com.devtime.contract.domain.PeriodAdjustment;
import com.devtime.shared.persistence.SoftDeleteRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link PeriodAdjustment} (spec 011 §25).
 *
 * <p>Não expõe nenhum método de atualização ou remoção, e isso é parte da garantia (RN-236,
 * INV-ADJ-01): o que não tem método não é feito por engano. Correção se faz por estorno.
 */
@Repository
public interface PeriodAdjustmentRepository extends SoftDeleteRepository<PeriodAdjustment> {

    /** Extrato de ajustes do período, em ordem cronológica (§10 de contracts.md). */
    @Query(
            """
            SELECT a FROM PeriodAdjustment a
             WHERE a.contractPeriodId = :periodId
             ORDER BY a.appliedAt ASC
            """)
    List<PeriodAdjustment> findByPeriod(@Param("periodId") UUID periodId);

    /**
     * Soma real dos ajustes do período.
     *
     * <p>Usada na reconciliação: {@code contract_periods.adjustment_minutes} é desnormalizado por
     * incremento, e o fechamento é o último momento em que uma divergência ainda pode ser corrigida
     * antes de o snapshot congelar o número.
     */
    @Query(
            """
            SELECT COALESCE(SUM(a.minutes), 0) FROM PeriodAdjustment a
             WHERE a.contractPeriodId = :periodId
            """)
    int sumMinutesByPeriod(@Param("periodId") UUID periodId);
}
