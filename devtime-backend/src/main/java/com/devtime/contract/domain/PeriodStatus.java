package com.devtime.contract.domain;

/**
 * Situação do período de apuração (state-machines.md §4.6).
 *
 * <p>Exatamente cinco valores. O cancelamento de período é representado por exclusão lógica, não
 * por um valor de enum.
 *
 * <p>{@code CLOSING}, {@code CLOSED} e {@code REOPENED} são produzidos por {@code 011-bank-hours};
 * {@code 004} cria e mantém o período até {@code OPEN}.
 */
public enum PeriodStatus {
    SCHEDULED,
    OPEN,
    CLOSING,
    CLOSED,
    REOPENED
}
