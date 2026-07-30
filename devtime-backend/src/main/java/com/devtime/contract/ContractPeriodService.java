package com.devtime.contract;

import com.devtime.contract.dto.ContractResponses.ContractPeriodResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ciclo de vida estrutural dos períodos (spec 004 §22.2).
 *
 * <p>Fronteira com {@code 011-bank-hours}: aqui o período é criado, aberto e consultado. Saldo,
 * extrato, ajustes e fechamento pertencem a {@code 011}.
 */
public interface ContractPeriodService {

    List<ContractPeriodResponse> listByContract(UUID contractId);

    ContractPeriodResponse getById(UUID periodId);

    /** Período aberto do contrato, quando houver. */
    java.util.Optional<ContractPeriodResponse> getCurrentPeriod(UUID contractId);

    /**
     * RN-107: período cujo intervalo fechado contém a data de trabalho.
     *
     * <p>Interface pública para {@code 008-worklogs}. Toda hora precisa pertencer a um ciclo de
     * apuração; a ausência de período para a data é o que rejeita o registro.
     */
    java.util.Optional<ContractPeriodResponse> resolveOpenPeriod(
            UUID contractId, LocalDate workDate);
}
