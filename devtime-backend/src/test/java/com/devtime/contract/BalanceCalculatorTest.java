package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.contract.domain.PeriodBalance;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Fórmulas canônicas do banco de horas (RN-218 a RN-223, §6.1 de specs/011).
 *
 * <p>O saldo é <b>o número</b> do produto: o que o cliente pergunta, o que sustenta a fatura e o
 * que aparece no topo do painel. RP-03 classifica erro de cálculo aqui como risco crítico, e SQ-10
 * determina que uma divergência reportada bloqueia toda a fila de desenvolvimento — esta suíte é o
 * oráculo que impede isso.
 */
class BalanceCalculatorTest {

    private final BalanceCalculator calculator = new BalanceCalculator();

    @Test
    @DisplayName("RN-218 a RN-222: o exemplo normativo da §6.1 é reproduzido integralmente")
    void normativeExample() {
        PeriodBalance balance = calculator.calculate(2400, 300, 60, 2900, 0);

        assertThat(balance.availableMinutes()).as("RN-218: 2400 + 300 + 60").isEqualTo(2760);
        assertThat(balance.remainingMinutes())
                .as("RN-220: pode ser negativo — é o excedente por outro ângulo")
                .isEqualTo(-140);
        assertThat(balance.overageMinutes()).as("RN-221: max(0, 2900 − 2760)").isEqualTo(140);
        assertThat(balance.consumptionRate())
                .as("RN-222: 2900/2760 × 100, com 2 casas — nunca 105.06999999")
                .isEqualByComparingTo(new BigDecimal("105.07"));
    }

    @ParameterizedTest(name = "contratado={0} transportado={1} ajuste={2} consumido={3}")
    @CsvSource({
        "2400,   0,   0, 1800,  2400,  600,   0",
        "2400, 300,   0, 1800,  2700,  900,   0",
        "2400,   0,  60, 2900,  2460, -440, 440",
        "2400,   0, -60, 2400,  2340,  -60,  60",
        "2400,   0,   0, 2400,  2400,    0,   0"
    })
    @DisplayName("RN-218/RN-220/RN-221: disponível, restante e excedente em aritmética inteira")
    void balanceArithmetic(
            int contracted,
            int carriedIn,
            int adjustment,
            int consumed,
            int expectedAvailable,
            int expectedRemaining,
            int expectedOverage) {
        PeriodBalance balance =
                calculator.calculate(contracted, carriedIn, adjustment, consumed, 0);

        assertThat(balance.availableMinutes()).isEqualTo(expectedAvailable);
        assertThat(balance.remainingMinutes()).isEqualTo(expectedRemaining);
        assertThat(balance.overageMinutes()).isEqualTo(expectedOverage);
    }

    @Nested
    @DisplayName("RN-222: taxa de consumo")
    class ConsumptionRate {

        @Test
        @DisplayName("RN-222/CX-01: disponível zero e consumo zero produzem 0%, não erro")
        void zeroAvailableAndZeroConsumed() {
            assertThat(calculator.calculate(0, 0, 0, 0, 0).consumptionRate())
                    .isEqualByComparingTo(new BigDecimal("0.00"));
        }

        @Test
        @DisplayName("RN-222/CX-02: disponível zero com consumo produz 100%, não divisão por zero")
        void zeroAvailableWithConsumption() {
            assertThat(calculator.calculate(0, 0, 0, 120, 0).consumptionRate())
                    .as(
                            "um período sem saldo que recebeu horas está 100% consumido, não indefinido")
                    .isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("RN-222/CX-03: contrato HOURLY_OPEN mantém taxa zero sem alerta")
        void hourlyOpenHasNoRate() {
            // RN-210: contractedMinutes = 0 por definição. O ramo de disponível zero existe para
            // este modelo comercial, não como defesa contra divisão por zero.
            assertThat(calculator.calculate(0, 0, 0, 0, 480).consumptionRate())
                    .isEqualByComparingTo(new BigDecimal("0.00"));
        }

        @Test
        @DisplayName("RN-222: a taxa tem sempre 2 casas, arredondadas HALF_UP")
        void rateAlwaysHasTwoDecimals() {
            assertThat(calculator.calculate(3, 0, 0, 1, 0).consumptionRate().scale()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("RN-223: minutos não faturáveis não entram no consumo")
    void nonBillableStaysOutOfConsumption() {
        PeriodBalance balance = calculator.calculate(2400, 0, 0, 600, 180);

        assertThat(balance.consumedMinutes()).isEqualTo(600);
        assertThat(balance.nonBillableMinutes()).isEqualTo(180);
        assertThat(balance.remainingMinutes())
                .as("os 180 não faturáveis não reduzem o saldo")
                .isEqualTo(1800);
    }

    @Test
    @DisplayName("BR-150: o cálculo é determinístico — mesma entrada, mesma saída")
    void calculationIsDeterministic() {
        assertThat(calculator.calculate(2400, 300, 60, 2900, 120))
                .isEqualTo(calculator.calculate(2400, 300, 60, 2900, 120));
    }
}
