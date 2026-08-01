package com.devtime.contract;

import com.devtime.contract.dto.BalanceRequests.ReopenPeriodRequest;
import com.devtime.contract.dto.BalanceResponses.ReopenPeriodResponse;
import java.util.UUID;

/**
 * Reabertura de período fechado (RN-242 a RN-244).
 *
 * <p>É a operação que altera um relatório <b>já entregue ao cliente</b>. Por isso exige {@code
 * ADMIN}/{@code OWNER}, justificativa registrada e a ordem inversa de RN-244 — e por isso o
 * snapshot anterior é <b>preservado</b> (INV-SNP-01): ele documenta o que foi entregue, e apagá-lo
 * eliminaria a prova de que o número mudou.
 */
public interface PeriodReopeningService {

    ReopenPeriodResponse reopen(UUID periodId, ReopenPeriodRequest request);
}
