package com.devtime.worklog;

import com.devtime.contract.BalanceService;
import com.devtime.contract.dto.BalanceResponses.OverageCheckResponse;
import com.devtime.worklog.domain.WorkLogExceptions;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogWarning;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Aplicação da política de excedente no registro de horas (RN-231 a RN-234).
 *
 * <p>BR-067: uma decisão por valor do enum. O <b>fato</b> — o registro ultrapassaria o saldo? — vem
 * de {@code 011} pela interface pública; a <b>consequência</b> é desta feature, porque é aqui que
 * ela se manifesta: rejeitar, avisar ou permitir a criação.
 *
 * <p><b>RN-234: em {@code BLOCK}, o registro não é dividido automaticamente</b> (CP-07, CX-22).
 * Faltando cinco minutos de saldo, a rejeição é integral. O sistema nunca decide sozinho quanto do
 * trabalho é faturável (PR-03) — a escolha entre reduzir o tempo, marcar como não faturável ou
 * pedir um ajuste é de quem trabalhou, e a mensagem de erro carrega os minutos disponíveis para que
 * ela seja possível.
 *
 * <p>CX-21: um registro com {@code billable = false} nunca chega a exceder — {@code billableMinutes
 * = 0} não consome saldo (RN-112, RN-223).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OveragePolicyEvaluator {

    private static final String BLOCK = "BLOCK";
    private static final String WARN = "WARN";

    private final BalanceService balanceService;

    /**
     * @param additionalBillableMinutes minutos faturáveis que o registro acrescentaria; zero em
     *     registro não faturável
     * @return avisos a devolver no {@code 201}; vazio quando não há excedente ou a política não
     *     avisa
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2220} / {@code 422} em
     *     {@code BLOCK}
     */
    public List<WorkLogWarning> evaluate(UUID periodId, int additionalBillableMinutes) {
        if (additionalBillableMinutes <= 0) {
            return List.of();
        }
        OverageCheckResponse check =
                balanceService.checkOverage(periodId, additionalBillableMinutes);
        if (!check.wouldExceed()) {
            return List.of();
        }

        if (BLOCK.equals(check.overagePolicy())) {
            log.info(
                    "excedente bloqueado periodId={} availableMinutes={} requestedMinutes={}",
                    periodId,
                    check.availableMinutes(),
                    additionalBillableMinutes);
            throw WorkLogExceptions.balanceInsufficient(
                    check.availableMinutes(),
                    check.consumedMinutes(),
                    additionalBillableMinutes); // RN-231, RN-234
        }

        if (WARN.equals(check.overagePolicy())) {
            log.info(
                    "excedente permitido com aviso periodId={} exceedingMinutes={}",
                    periodId,
                    check.exceedingMinutes());
            return List.of(
                    new WorkLogWarning(
                            com.devtime.shared.error.ErrorCode.PERIOD_OVERAGE_WARNING.getCode(),
                            "Aviso: saldo do contrato excedido",
                            check.exceedingMinutes())); // RN-232
        }

        // RN-233 — ALLOW_BILLABLE: permite sem aviso. O excedente é marcado para cobrança à
        // overageRate nos relatórios de 012, a partir de overageMinutes do próprio período; nada
        // precisa ser registrado no work log para isso.
        log.info(
                "excedente permitido para cobrança periodId={} exceedingMinutes={}",
                periodId,
                check.exceedingMinutes());
        return List.of();
    }
}
