package com.devtime.contract;

import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.shared.maintenance.DenormalizationReconciler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconcilia {@code consumedMinutes} e {@code nonBillableMinutes} de períodos <b>abertos</b> (spec
 * 011 §22.4).
 *
 * <p><b>Apenas abertos, e a restrição é a regra e não uma otimização.</b> Reconciliar um período
 * fechado alteraria um valor congelado: o snapshot é a verdade daquele ciclo (RN-701), e
 * divergência nele é detectada por {@code SnapshotIntegrityJob}, que <b>alerta sem corrigir</b>
 * (CX-21). Um job que "arrumasse" o número de um período fechado mudaria, em silêncio, uma fatura
 * já enviada.
 *
 * <p>É a mesma agregação do passo 1 do fechamento (FA-16), rodando antes e não no último instante:
 * quanto mais cedo a divergência aparece, mais chance há de descobrir o incremento que se perdeu.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PeriodConsumptionReconciler implements DenormalizationReconciler {

    private final ContractPeriodRepository periodRepository;
    private final PeriodAdjustmentRepository adjustmentRepository;
    private final List<PeriodWorkLogSource> workLogSources;

    @Override
    public String target() {
        return "contractPeriod.consumedMinutes";
    }

    @Override
    @Transactional
    public int reconcile() {
        List<ContractPeriod> open =
                periodRepository.findByStatusIn(List.of(PeriodStatus.OPEN, PeriodStatus.REOPENED));

        int corrected = 0;
        for (ContractPeriod period : open) {
            int realConsumed = sum(period, PeriodWorkLogSource::sumBillableMinutes);
            int realNonBillable = sum(period, PeriodWorkLogSource::sumNonBillableMinutes);
            int realAdjustment = adjustmentRepository.sumMinutesByPeriod(period.getId());

            if (period.getConsumedMinutes() != realConsumed
                    || period.getNonBillableMinutes() != realNonBillable
                    || period.getAdjustmentMinutes() != realAdjustment) {
                log.warn(
                        "desnormalizado de período divergente periodId={} consumed={}->{}"
                                + " nonBillable={}->{} adjustment={}->{}",
                        period.getId(),
                        period.getConsumedMinutes(),
                        realConsumed,
                        period.getNonBillableMinutes(),
                        realNonBillable,
                        period.getAdjustmentMinutes(),
                        realAdjustment);
                period.setConsumedMinutes(realConsumed);
                period.setNonBillableMinutes(realNonBillable);
                period.setAdjustmentMinutes(realAdjustment);
                corrected++;
            }
        }
        return corrected;
    }

    private int sum(
            ContractPeriod period,
            java.util.function.ToIntBiFunction<PeriodWorkLogSource, java.util.UUID> query) {
        return workLogSources.stream()
                .mapToInt(source -> query.applyAsInt(source, period.getId()))
                .sum();
    }
}
