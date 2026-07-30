package com.devtime.shared.time;

import java.time.LocalDate;

/**
 * Intervalo de datas de calendário (entities.md §7.2).
 *
 * <p>BR-149: intervalo de datas é <b>fechado</b> — {@code [início, fim]}, ambos inclusive. Difere
 * deliberadamente do intervalo de instantes, que é semi-aberto {@code [início, fim)} (BR-148): um
 * período de contrato que termina em 31/08 inclui o dia 31 inteiro, enquanto duas sessões de
 * trabalho consecutivas podem se tocar exatamente às 10:00 sem conflitar.
 *
 * @param start data inicial, inclusive
 * @param end data final, inclusive
 */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("DateRange exige start e end");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("DateRange exige end >= start");
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public boolean overlaps(DateRange other) {
        return !other.end().isBefore(start) && !other.start().isAfter(end);
    }

    /** Número de dias do intervalo, contando ambas as extremidades. */
    public long lengthInDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
    }
}
