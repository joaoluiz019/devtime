package com.devtime.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tabela normativa de cálculo (RN-110 a RN-113, §6.3 de specs/008-worklogs) — T-008-05.
 *
 * <p><b>Escrita antes do código</b> (SQ-02). A direção do arredondamento e o truncamento dos
 * segundos são as duas decisões desta feature em que um erro cobra do cliente tempo que não foi
 * trabalhado — a violação de confiança mais direta que o produto poderia cometer (PR-03).
 */
class WorkLogCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    private final WorkLogCalculator calculator = new WorkLogCalculator();
    private final RoundingPolicy roundingPolicy = new RoundingPolicy();

    /**
     * Os oito cenários da tabela normativa de §6.3.
     *
     * <p>Os dois últimos da tabela — 25 horas e pausa total — são rejeições, e por isso verificam o
     * valor que dispara a rejeição em vez de um resultado válido.
     */
    @ParameterizedTest(name = "[{index}] {6}")
    @CsvSource({
        "09:00:00, 11:30:00,  0,  0, 150, 150, 'normal'",
        "09:00:00, 11:30:59,  0,  0, 150, 150, 'com segundos — truncados, nunca arredondados'",
        "09:00:00, 12:00:00, 25,  0, 180, 155, 'com pausa'",
        "14:00:00, 15:00:00,  0,  0,  60,  60, 'não faturável — o líquido é o mesmo; o faturável é que zera'",
        "09:00:00, 10:52:00,  0, 15, 112, 105, 'arredondamento de 15 sobre 112 produz 105, nunca 120'"
    })
    @DisplayName("RN-110/RN-111/RN-113: a tabela normativa de cálculo da §6.3 é reproduzida")
    void normativeCalculationTable(
            String start,
            String end,
            int pausedMinutes,
            int roundingMinutes,
            int expectedGross,
            int expectedNet,
            String scenario) {
        int gross = calculator.grossMinutes(instant(start), instant(end));
        int net =
                roundingPolicy.roundDown(
                        calculator.netMinutes(gross, pausedMinutes), roundingMinutes);

        assertThat(gross).as("grossMinutes — " + scenario).isEqualTo(expectedGross);
        assertThat(net).as("netMinutes — " + scenario).isEqualTo(expectedNet);
    }

    @Test
    @DisplayName("RN-110/RN-108: sessão 22h→01h30 produz 210 minutos em um registro único")
    void midnightCrossingProducesASingleDuration() {
        Instant started = DAY.atTime(22, 0).toInstant(ZoneOffset.UTC);
        Instant ended = DAY.plusDays(1).atTime(1, 30).toInstant(ZoneOffset.UTC);

        assertThat(calculator.grossMinutes(started, ended)).isEqualTo(210);
    }

    @Test
    @DisplayName("RN-103: sessão de 25 horas produz 1.500 minutos e ultrapassa o limite de 1.440")
    void twentyFiveHourSessionExceedsTheLimit() {
        Instant started = DAY.atTime(8, 0).toInstant(ZoneOffset.UTC);
        Instant ended = DAY.plusDays(1).atTime(9, 0).toInstant(ZoneOffset.UTC);

        assertThat(calculator.grossMinutes(started, ended)).isEqualTo(1500).isGreaterThan(1440);
    }

    @Test
    @DisplayName("RN-115: pausa igual ao tempo bruto zera o líquido")
    void fullPauseZeroesTheNet() {
        assertThat(calculator.netMinutes(60, 60)).isZero();
    }

    @Nested
    @DisplayName("RN-010: truncamento de segundos")
    class SecondTruncation {

        @ParameterizedTest(name = "{0} segundos extras continuam produzindo 150 minutos")
        @ValueSource(ints = {0, 1, 30, 58, 59})
        @DisplayName("RN-010/CA-06: segundos são truncados por divisão inteira, nunca arredondados")
        void secondsAreAlwaysTruncated(int extraSeconds) {
            Instant started = instant("09:00:00");
            Instant ended = instant("11:30:00").plusSeconds(extraSeconds);

            assertThat(calculator.grossMinutes(started, ended)).isEqualTo(150);
        }

        @Test
        @DisplayName("RN-010: 59 segundos não viram um minuto; 60 viram")
        void sixtySecondsBecomeOneMinute() {
            assertThat(calculator.grossMinutes(instant("09:00:00"), instant("09:00:59"))).isZero();
            assertThat(calculator.grossMinutes(instant("09:00:00"), instant("09:01:00")))
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("RN-113: direção do arredondamento")
    class RoundingDirection {

        @ParameterizedTest(name = "net={0} com múltiplo {1} ⇒ {2}")
        @CsvSource({
            "112, 15, 105",
            "119, 15, 105",
            "120, 15, 120",
            "112,  0, 112",
            "7,    5,   5",
            "4,    5,   0",
            "59,  30,  30",
            "150,  6, 150",
            "151, 10, 150"
        })
        @DisplayName("RN-113/CP-06: o arredondamento é sempre PARA BAIXO; 0 desativa")
        void roundingIsAlwaysDownwards(int net, int rounding, int expected) {
            assertThat(roundingPolicy.roundDown(net, rounding)).isEqualTo(expected);
        }

        @Test
        @DisplayName(
                "RN-113/OB-05: 10 minutos com múltiplo 15 resultam em 0 — correto e contraintuitivo")
        void roundingCanZeroTheNet() {
            // A alternativa seria arredondar para cima, cobrando 15 minutos por 10 trabalhados
            // (PR-03). O registro é então rejeitado por RN-115, com o valor bruto na resposta.
            assertThat(roundingPolicy.roundDown(10, 15)).isZero();
        }

        @Test
        @DisplayName("RN-113: nenhum resultado excede o valor de entrada")
        void roundingNeverIncreasesTheValue() {
            for (int net = 0; net <= 480; net++) {
                for (int rounding : new int[] {0, 5, 6, 10, 15, 30}) {
                    assertThat(roundingPolicy.roundDown(net, rounding))
                            .as("net=%d rounding=%d", net, rounding)
                            .isLessThanOrEqualTo(net);
                }
            }
        }
    }

    @Nested
    @DisplayName("RN-112: minutos faturáveis")
    class BillableMinutes {

        @Test
        @DisplayName("RN-112: faturável reproduz o líquido; não faturável zera (RN-223)")
        void billableMirrorsNetOnlyWhenBillable() {
            assertThat(calculator.billableMinutes(150, true)).isEqualTo(150);
            assertThat(calculator.billableMinutes(150, false)).isZero();
        }
    }

    private static Instant instant(String time) {
        return DAY.atTime(LocalTime.parse(time)).toInstant(ZoneOffset.UTC);
    }
}
