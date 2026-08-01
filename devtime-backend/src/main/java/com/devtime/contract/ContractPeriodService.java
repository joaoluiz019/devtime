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

    /**
     * RN-107, na forma que {@code 008-worklogs} consegue consumir (AR-02).
     *
     * <p>{@link ContractPeriodResponse} expõe {@code PeriodStatus}, enum do domínio de {@code 004}.
     * A referência traz {@code status} como texto e {@code acceptsWorkLogs} já decidido — {@code
     * OPEN} e {@code REOPENED} aceitam escrita (§4.6 de state-machines.md, CX-24).
     */
    java.util.Optional<com.devtime.contract.dto.ContractResponses.ContractPeriodRefResponse>
            resolvePeriodRef(UUID contractId, LocalDate workDate);

    /**
     * Período por identificador, como referência.
     *
     * <p>Interface pública para {@code 008} (RN-121, RN-124) e {@code 009}.
     *
     * @throws com.devtime.shared.error.EntityNotFoundException {@code DEVTIME-2002} quando
     *     inexistente ou de outro tenant
     */
    com.devtime.contract.dto.ContractResponses.ContractPeriodRefResponse getRefById(UUID periodId);
}
