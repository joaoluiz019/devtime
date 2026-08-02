package com.devtime.contract;

import com.devtime.audit.AuditService;
import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodPlan;
import com.devtime.contract.domain.PeriodSpec;
import com.devtime.contract.domain.PeriodStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Materialização do plano de períodos em entidades persistidas (passos 1 a 10 da §6.2).
 *
 * <p>Extraído de {@code ContractServiceImpl} quando a geração automática de {@code S4} passou a
 * precisar exatamente do mesmo caminho: ativação, retomada e o {@code GeneratePeriodsJob} criam
 * períodos com as mesmas regras, e duas implementações divergiriam na primeira correção feita em
 * apenas uma delas. É a mesma razão pela qual a prévia e a geração real compartilham {@link
 * PeriodGenerator} (CA-01).
 *
 * <p>A contiguidade é verificada <b>antes</b> de qualquer escrita (RN-216, passo 10): um período
 * fora de sequência já persistido exigiria correção manual em produção, e INV-PER-02/03 não admitem
 * lacuna nem sobreposição.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PeriodMaterializer {

    private final ContractPeriodRepository periodRepository;
    private final PeriodGenerator periodGenerator;
    private final PeriodContiguityValidator contiguityValidator;
    private final AuditService auditService;

    /**
     * @param previousEndDate fim do último período existente; nulo gera a partir de {@code
     *     contract.startDate}
     * @param status {@code OPEN} na ativação, {@code SCHEDULED} na renovação antecipada, {@code
     *     CLOSED} nos períodos retroativos de migração (CE-06)
     */
    public List<ContractPeriod> materialize(
            Contract contract,
            LocalDate previousEndDate,
            int previousSequence,
            int count,
            PeriodStatus status) {
        PeriodSpec spec = specOf(contract);
        List<PeriodPlan> plans =
                previousEndDate == null
                        ? periodGenerator.generate(spec, count)
                        : periodGenerator.generateAfter(
                                spec, previousEndDate, previousSequence, count);

        // Passo 10: contiguidade verificada antes de qualquer escrita (RN-216).
        contiguityValidator.assertContiguous(contract.getId(), previousEndDate, plans);

        List<ContractPeriod> created = new ArrayList<>();
        for (PeriodPlan plan : plans) {
            ContractPeriod period = new ContractPeriod();
            period.setContractId(contract.getId());
            period.setSequence(plan.sequence());
            period.setLabel(plan.label());
            period.setStartDate(plan.startDate());
            period.setEndDate(plan.endDate());
            period.setStatus(status);
            period.setContractedMinutes(plan.contractedMinutes());
            // Passo 9: congela os valores do contrato no período (§6.7 de entities.md).
            period.setHourlyRateSnapshot(contract.getHourlyRate());
            period.setOverageRateSnapshot(contract.getOverageRate());
            period.setCurrency(contract.getCurrency());
            ContractPeriod saved = periodRepository.save(period);
            created.add(saved);

            auditService.recordSystemAction(
                    "PERIOD_CREATED",
                    "ContractPeriod",
                    saved.getId(),
                    Map.of(
                            "sequence", String.valueOf(plan.sequence()),
                            "startDate", plan.startDate().toString(),
                            "endDate", plan.endDate().toString(),
                            "contractedMinutes", String.valueOf(plan.contractedMinutes()),
                            "status", status.name()));
            log.info(
                    "período gerado contractId={} sequence={} {} a {} contractedMinutes={}"
                            + " status={}",
                    contract.getId(),
                    plan.sequence(),
                    plan.startDate(),
                    plan.endDate(),
                    plan.contractedMinutes(),
                    status);
        }
        return created;
    }

    public PeriodSpec specOf(Contract contract) {
        return new PeriodSpec(
                contract.getType(),
                contract.getMonthlyMinutes(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getBillingDay(),
                contract.isProrateFirstPeriod());
    }
}
