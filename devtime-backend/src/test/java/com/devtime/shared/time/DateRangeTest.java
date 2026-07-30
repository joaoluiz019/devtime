package com.devtime.shared.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Semântica do intervalo de datas (BR-149). */
class DateRangeTest {

    private final DateRange august =
            new DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

    @Test
    @DisplayName("BR-149: o intervalo de datas é fechado — inclui as duas extremidades")
    void rangeMustBeClosed() {
        assertThat(august.contains(LocalDate.of(2026, 8, 1))).isTrue();
        assertThat(august.contains(LocalDate.of(2026, 8, 31)))
                .as("um período que termina em 31/08 inclui o dia 31 inteiro")
                .isTrue();
        assertThat(august.contains(LocalDate.of(2026, 7, 31))).isFalse();
        assertThat(august.contains(LocalDate.of(2026, 9, 1))).isFalse();
    }

    @Test
    @DisplayName("BR-149: intervalos de datas adjacentes não se sobrepõem")
    void adjacentRangesMustNotOverlap() {
        var september = new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(august.overlaps(september)).isFalse();
        assertThat(september.overlaps(august)).isFalse();
    }

    @Test
    @DisplayName("BR-149: intervalos que compartilham um único dia se sobrepõem")
    void rangesSharingOneDayMustOverlap() {
        var crossing = new DateRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 15));

        assertThat(august.overlaps(crossing)).isTrue();
    }

    @Test
    @DisplayName("O comprimento conta ambas as extremidades")
    void lengthMustCountBothEnds() {
        assertThat(august.lengthInDays()).isEqualTo(31);
        assertThat(new DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)).lengthInDays())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("CG-06: intervalo invertido é rejeitado na construção")
    void invertedRangeMustBeRejected() {
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CG-06: extremidade nula é rejeitada na construção")
    void nullBoundMustBeRejected() {
        assertThatThrownBy(() -> new DateRange(null, LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 8, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
