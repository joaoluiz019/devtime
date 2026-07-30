package com.devtime.contract;

import com.devtime.contract.domain.ContractExceptions;
import com.devtime.contract.domain.PeriodPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Contiguidade e ausência de sobreposição entre períodos (RN-216, INV-PER-02/03).
 *
 * <p>Executado <b>antes</b> de persistir (passo 10 da §6.2): a constraint {@code
 * ex_periods_no_overlap} é a barreira final, mas uma violação detectada nela chegaria como erro de
 * banco, sem o contexto de qual sequência divergiu.
 *
 * <p>Uma falha aqui é erro crítico, não erro do usuário: significa que o gerador produziu uma
 * sequência inconsistente. O log em nível {@code ERROR} com as datas esperada e obtida é o que
 * permite reconstruir o caso — a métrica {@code period.contiguity.violation} de §29 tem alerta em
 * qualquer ocorrência.
 */
@Component
@Slf4j
public class PeriodContiguityValidator {

    /**
     * @param previousEndDate fim do último período já persistido; nulo quando não há nenhum
     * @throws com.devtime.shared.error.BusinessRuleException falha crítica de contiguidade
     */
    public void assertContiguous(
            UUID contractId, LocalDate previousEndDate, List<PeriodPlan> plans) {
        LocalDate expectedStart = previousEndDate == null ? null : previousEndDate.plusDays(1);

        for (PeriodPlan plan : plans) {
            if (expectedStart != null && !plan.startDate().equals(expectedStart)) {
                log.error(
                        "falha de contiguidade contractId={} sequence={} esperado={} obtido={}",
                        contractId,
                        plan.sequence(),
                        expectedStart,
                        plan.startDate());
                throw ContractExceptions.contiguityViolation(
                        contractId, expectedStart, plan.startDate()); // RN-216
            }
            if (plan.endDate().isBefore(plan.startDate())) {
                log.error(
                        "período com fim anterior ao início contractId={} sequence={}",
                        contractId,
                        plan.sequence());
                throw ContractExceptions.contiguityViolation(
                        contractId, plan.startDate(), plan.endDate()); // INV-PER-04
            }
            expectedStart = plan.endDate().plusDays(1);
        }
    }
}
