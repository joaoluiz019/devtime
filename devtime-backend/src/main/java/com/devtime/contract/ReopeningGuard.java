package com.devtime.contract;

import com.devtime.contract.domain.BalanceExceptions;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Guarda da reabertura de período (RN-244).
 *
 * <p><b>A reabertura vai do mais recente para o mais antigo.</b> O {@code carriedIn} do período
 * seguinte derivou do {@code carriedOut} deste; reabrir este sem reabrir aquele invalidaria um
 * período já congelado — e o cliente veria o saldo de um mês fechado mudar sozinho.
 *
 * <p>SG-08: a guarda é verificada <b>a cada</b> reabertura, não apenas na primeira. Numa cascata de
 * três períodos (CX-16), cada passo é validado de novo contra o estado corrente, então não há ordem
 * de chamadas capaz de burlar a regra.
 */
@Component
@RequiredArgsConstructor
public class ReopeningGuard {

    private final ContractPeriodRepository repository;

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2244} / {@code 409}
     *     indicando qual período reabrir primeiro
     */
    public void assertReopenable(ContractPeriod period) {
        if (period.getStatus() != PeriodStatus.CLOSED) {
            throw BalanceExceptions.invalidPeriodTransition(period.getStatus(), "REOPEN"); // ME-04
        }
        List<ContractPeriod> laterClosed =
                repository.findClosedAfter(period.getContractId(), period.getSequence());
        if (!laterClosed.isEmpty()) {
            // O mais próximo é o que precisa ser reaberto primeiro — indicá-lo transforma o erro
            // em instrução.
            ContractPeriod next = laterClosed.get(0);
            throw BalanceExceptions.laterPeriodClosed(next.getId(), next.getSequence());
        }
    }
}
