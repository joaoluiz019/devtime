package com.devtime.contract;

import com.devtime.contract.domain.ContractExceptions;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.RolloverPolicy;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Coerência entre tipo e campos do contrato (INV-CTR-02, INV-CTR-03, INV-CTR-04).
 *
 * <p>A ordem das verificações reproduz a §6.1 da spec (passos 4 a 7) e é normativa (BR-062). As
 * mesmas condições existem como {@code CHECK} em V012: a validação na aplicação produz o código de
 * erro específico e o campo culpado; a constraint impede que qualquer caminho — inclusive um {@code
 * INSERT} direto — grave um contrato incoerente.
 */
@Component
public class ContractTypeCoherenceValidator {

    private static final int MAX_MONTHLY_MINUTES = 44_640; // RN-202: 31 dias

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2202}, {@code 2203},
     *     {@code 2204}, {@code 2209} ou {@code 2210}, conforme a violação
     */
    public void assertCoherent(
            ContractType type,
            Integer monthlyMinutes,
            RolloverPolicy rolloverPolicy,
            Integer rolloverCapMinutes,
            int billingDay,
            LocalDate startDate,
            LocalDate endDate) {
        // Passo 4 — INV-CTR-02 e INV-CTR-03.
        if (type == ContractType.MONTHLY_HOURS) {
            if (monthlyMinutes == null
                    || monthlyMinutes < 1
                    || monthlyMinutes > MAX_MONTHLY_MINUTES) {
                throw ContractExceptions.monthlyMinutesRequired(); // RN-202
            }
        } else {
            if (monthlyMinutes != null) {
                throw ContractExceptions.typeIncoherent(
                        "HOURLY_OPEN não aceita monthlyMinutes"); // CX-08
            }
            if (rolloverPolicy != null && rolloverPolicy != RolloverPolicy.NONE) {
                throw ContractExceptions.typeIncoherent(
                        "HOURLY_OPEN não aceita rollover"); // RN-210
            }
        }
        // Passo 5 — RN-203.
        if (billingDay < 1 || billingDay > 28) {
            throw ContractExceptions.billingDayInvalid(billingDay);
        }
        // Passo 6 — RN-204 / INV-CTR-05.
        if (endDate != null && endDate.isBefore(startDate)) {
            throw ContractExceptions.dateRangeInvalid();
        }
        // Passo 7 — INV-CTR-04.
        if (rolloverPolicy == RolloverPolicy.CAPPED && rolloverCapMinutes == null) {
            throw ContractExceptions.rolloverCapRequired();
        }
    }
}
