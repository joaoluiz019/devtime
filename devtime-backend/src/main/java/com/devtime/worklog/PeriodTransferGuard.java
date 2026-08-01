package com.devtime.worklog;

import com.devtime.contract.dto.ContractResponses.ContractPeriodRefResponse;
import com.devtime.worklog.domain.WorkLogExceptions;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Movimentação de um registro entre períodos (RN-124).
 *
 * <p>Alterar {@code workDate} pode realocar o registro em outro período. Isso só é permitido quando
 * <b>ambos</b> — origem e destino — estão abertos: mover horas para um período fechado alteraria um
 * relatório já emitido, e retirá-las de um período fechado faria o mesmo do outro lado.
 *
 * <p>{@code REOPENED} conta como aberto (CX-24, CX-18): a reabertura existe justamente para
 * permitir a correção, e recusá-la aqui esvaziaria o propósito da operação. A decisão chega pronta
 * em {@code acceptsWorkLogs}, tomada por {@code 004} (AR-02).
 */
@Component
@Slf4j
public class PeriodTransferGuard {

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2124} / {@code 409}
     *     quando origem ou destino não aceitam escrita
     */
    public void assertTransferable(
            ContractPeriodRefResponse source, ContractPeriodRefResponse target) {
        if (source.id().equals(target.id())) {
            return;
        }
        UUID blocked = firstBlocked(source, target);
        if (blocked != null) {
            log.info(
                    "mudança de período bloqueada sourcePeriodId={} targetPeriodId={}",
                    source.id(),
                    target.id());
            throw WorkLogExceptions.periodTransferBlocked(blocked);
        }
        // §28: WARN. É raro e é a primeira coisa a verificar quando horas "sumiram" de um mês.
        log.warn(
                "work log movido entre períodos sourcePeriodId={} targetPeriodId={}",
                source.id(),
                target.id());
    }

    /** O destino é verificado primeiro: é o erro que o usuário consegue corrigir sozinho. */
    private UUID firstBlocked(ContractPeriodRefResponse source, ContractPeriodRefResponse target) {
        if (!target.acceptsWorkLogs()) {
            return target.id();
        }
        if (!source.acceptsWorkLogs()) {
            return source.id();
        }
        return null;
    }
}
