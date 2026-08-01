package com.devtime.worklog;

import com.devtime.worklog.domain.WorkLog;
import com.devtime.worklog.domain.WorkLogExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Imutabilidade do registro pertencente a período fechado (RN-121, INV-WKL-07).
 *
 * <p>Um relatório entregue ao cliente não muda silenciosamente (ART-005). A correção existe, mas
 * passa por reabertura formal e auditada do período em {@code 011-bank-hours} — que registra quem
 * reabriu e por quê, exatamente o que torna a alteração defensável em disputa contratual.
 *
 * <p>IMP-01 / SG-05: a guarda é aplicada no <b>serviço</b>, não no controller. Verificar apenas na
 * fronteira HTTP deixaria o caminho do cronômetro (RN-159) e o de qualquer chamador interno futuro
 * sem proteção.
 *
 * <p>OWN-02: <b>ownership não sobrepõe esta guarda</b>. O autor não edita o próprio registro
 * travado; ser dono do dado não dá autoridade sobre um número já faturado.
 */
@Component
@Slf4j
public class LockedPeriodGuard {

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2121} / {@code 409}
     */
    public void assertNotLocked(WorkLog workLog) {
        if (workLog.isLocked()) {
            log.info(
                    "edição bloqueada por período fechado workLogId={} contractPeriodId={}",
                    workLog.getId(),
                    workLog.getContractPeriodId());
            throw WorkLogExceptions.locked(workLog.getContractPeriodId());
        }
    }
}
