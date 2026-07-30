package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Rateio proporcional de período parcial (RN-217).
 *
 * <p>Escrita antes da implementação (T-004-47, regra SQ-02): a suíte é o oráculo do cálculo, não a
 * confirmação do que o código faz.
 */
class ProrationCalculatorTest {

    private final ProrationCalculator calculator = new ProrationCalculator();

    @Test
    @DisplayName("RN-217: exemplo normativo — 2.400 min em 22 de 31 dias resulta em 1.703")
    void shouldReproduceNormativeExample() {
        // business-rules.md §7.2: contrato de 40h, startDate 10/01, billingDay 1.
        assertThat(calculator.prorate(2400, 22, 31)).isEqualTo(1703);
    }

    @ParameterizedTest(name = "{0} min × {1}/{2} dias = {3}")
    @DisplayName("RN-217: rateio é round(monthlyMinutes × dias / diasDoCicloCheio)")
    @CsvSource({
        "2400, 31, 31, 2400",
        "2400, 1, 31, 77",
        "2400, 15, 30, 1200",
        "2400, 22, 31, 1703",
        "1000, 1, 3, 333",
        "1000, 2, 3, 667"
    })
    void shouldProrateByCalendarDays(
            int monthlyMinutes, int periodDays, int fullCycleDays, int expected) {
        assertThat(calculator.prorate(monthlyMinutes, periodDays, fullCycleDays))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("CX-09/ART-034: o arredondamento ocorre sobre inteiros, sem ponto flutuante")
    void shouldRoundHalfUpOnIntegers() {
        // 100 × 1/8 = 12,5 → 13 (meio para cima), sem passar por double em nenhum ponto.
        assertThat(calculator.prorate(100, 1, 8)).isEqualTo(13);
        // 100 × 3/8 = 37,5 → 38.
        assertThat(calculator.prorate(100, 3, 8)).isEqualTo(38);
    }

    @Test
    @DisplayName("CX-04: período de um único dia recebe 1/N do ciclo")
    void shouldProrateSingleDayPeriod() {
        assertThat(calculator.prorate(3000, 1, 30)).isEqualTo(100);
    }

    @Test
    @DisplayName("CG-06: ciclo de duração não positiva falha alto em vez de dividir por zero")
    void shouldRejectNonPositiveCycle() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> calculator.prorate(2400, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("BR-150: o cálculo é determinístico em execuções repetidas")
    void shouldBeDeterministic() {
        int first = calculator.prorate(2400, 22, 31);
        for (int repetition = 0; repetition < 100; repetition++) {
            assertThat(calculator.prorate(2400, 22, 31)).isEqualTo(first);
        }
    }
}
