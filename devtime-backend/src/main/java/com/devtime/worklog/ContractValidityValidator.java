package com.devtime.worklog;

import com.devtime.shared.time.TenantClock;
import com.devtime.worklog.domain.WorkLogExceptions;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Vigência contratual do registro de horas (RN-117).
 *
 * <p>Não se registra hora fora da janela em que o contrato existiu. A verificação é sobre {@code
 * startedAt}, e não sobre {@code endedAt}, pela mesma razão de RN-108: a sessão pertence ao momento
 * em que começou.
 *
 * <p>A comparação é feita sobre a <b>data local</b> do início no fuso do tenant, porque {@code
 * contract.startDate} e {@code contract.endDate} são datas de calendário (ART-031) e o intervalo
 * entre elas é <b>fechado</b> (BR-149): um contrato que termina em 31/08 aceita trabalho no dia 31
 * inteiro. Comparar um {@code Instant} com um {@code LocalDate} sem essa conversão rejeitaria as
 * últimas horas do último dia para tenants a oeste de Greenwich.
 */
@Component
@RequiredArgsConstructor
public class ContractValidityValidator {

    private final TenantClock clock;

    /**
     * @param contractEndDate pode ser nulo — contrato sem término definido
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2117} / {@code 422}
     */
    public void assertWithinValidity(
            Instant startedAt, LocalDate contractStartDate, LocalDate contractEndDate) {
        LocalDate startDateLocal = clock.toTenantDate(startedAt);
        boolean beforeStart = startDateLocal.isBefore(contractStartDate);
        boolean afterEnd = contractEndDate != null && startDateLocal.isAfter(contractEndDate);
        if (beforeStart || afterEnd) {
            throw WorkLogExceptions.outsideContractValidity(contractStartDate, contractEndDate);
        }
    }
}
