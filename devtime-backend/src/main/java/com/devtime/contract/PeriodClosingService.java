package com.devtime.contract;

import com.devtime.contract.dto.BalanceRequests.ClosePeriodRequest;
import com.devtime.contract.dto.BalanceResponses.ClosePeriodResponse;
import java.util.UUID;

/**
 * Fechamento de período (RN-239 a RN-241, RN-245).
 *
 * <p>É a operação que <b>congela o número que vai para a fatura</b>. Os sete passos de RN-241 são
 * atômicos: falha em qualquer um reverte todos. Um fechamento parcial deixaria work logs travados
 * sem snapshot, ou um snapshot sem o período fechado — estados dos quais não existe caminho de
 * volta automático.
 */
public interface PeriodClosingService {

    /**
     * Executa a sequência atômica de RN-241.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2239} (antes do fim sem
     *     confirmação), {@code DEVTIME-2240} (cronômetro ativo) ou {@code DEVTIME-2010} (estado
     *     incompatível)
     */
    ClosePeriodResponse close(UUID periodId, ClosePeriodRequest request);
}
