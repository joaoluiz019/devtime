package com.devtime.contract;

import com.devtime.contract.dto.BalanceResponses.PeriodWorkLogEntry;
import java.util.List;
import java.util.UUID;

/**
 * Origem dos registros de horas de um período (RN-219, RN-223, RN-241, RN-243).
 *
 * <p>{@code worklog} depende de {@code contract} — o período é resolvido por RN-107 e o saldo é
 * consultado por RN-231. Chamar {@code WorkLogService} a partir daqui fecharia o ciclo que AR-09 e
 * BR-008 proíbem, então a dependência é invertida: {@code contract} declara o que o fechamento
 * precisa, {@code worklog} implementa.
 *
 * <p>Sem implementação registrada, as somas são zero e o travamento não afeta linha alguma — o
 * fechamento continua correto, porque sem a feature de horas não existem registros a considerar.
 */
public interface PeriodWorkLogSource {

    /** RN-219: soma de {@code billableMinutes} dos registros não excluídos do período. */
    int sumBillableMinutes(UUID periodId);

    /** RN-223: soma dos minutos não faturáveis — visíveis no relatório, fora do saldo. */
    int sumNonBillableMinutes(UUID periodId);

    /**
     * RN-241 passo 3: trava os registros do período.
     *
     * @return quantidade travada, exibida no resumo do fechamento
     */
    int lockByPeriod(UUID periodId);

    /** RN-243: a reabertura limpa {@code lockedAt} dos registros do período. */
    int unlockByPeriod(UUID periodId);

    /**
     * Registros do período resolvidos para exibição.
     *
     * <p>Alimenta o extrato explicativo e o payload do snapshot. {@code ticketKey} e {@code
     * categoryName} chegam resolvidos porque o snapshot é imutável e precisa continuar legível anos
     * depois, mesmo que o ticket mude de nome ou a categoria seja excluída.
     */
    List<PeriodWorkLogEntry> findByPeriod(UUID periodId);
}
