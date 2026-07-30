package com.devtime.contract.domain;

import java.time.LocalDate;

/**
 * Período calculado, ainda não persistido (spec 004 §6.2).
 *
 * <p>É o que a prévia devolve e o que a ativação materializa em {@link ContractPeriod} — a mesma
 * estrutura nos dois caminhos, para que a prévia não possa divergir do resultado (CA-01).
 *
 * @param sequence posição no contrato, começando em 1 (INV-PER-01)
 * @param label rótulo derivado das datas (entities.md §6.7)
 * @param startDate início, inclusive
 * @param endDate fim, <b>inclusive</b> (entities.md §7.2)
 * @param contractedMinutes minutos do período, já rateados quando parcial (RN-217)
 * @param partial se o período cobre menos que um ciclo cheio
 * @param periodDays dias corridos do período
 * @param fullCycleDays dias corridos do ciclo cheio correspondente
 */
public record PeriodPlan(
        int sequence,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        int contractedMinutes,
        boolean partial,
        int periodDays,
        int fullCycleDays) {

    /**
     * Base do rateio exibida ao usuário, ex.: {@code "22 de 31 dias"} (contracts.md §5).
     *
     * <p>Mostrar a fração usada é o que permite ao usuário conferir o número antes de ativar o
     * contrato — sem ela, {@code contractedMinutes} seria um valor sem explicação.
     */
    public String prorationBasis() {
        return partial ? periodDays + " de " + fullCycleDays + " dias" : null;
    }
}
