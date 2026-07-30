package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.PeriodPlan;
import com.devtime.contract.domain.PeriodSpec;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Geração de períodos (RN-211, RN-212, RN-214, RN-217).
 *
 * <p>T-004-38 e T-004-39, escritas <b>antes</b> do gerador conforme a regra inegociável SQ-02. A
 * tabela normativa de §6.2 da spec é reproduzida célula a célula: é ela que define o comportamento
 * correto, não a implementação.
 */
class PeriodGeneratorTest {

    private final PeriodGenerator generator = new PeriodGenerator(new ProrationCalculator());

    private PeriodSpec monthly(LocalDate startDate, int billingDay) {
        return new PeriodSpec(ContractType.MONTHLY_HOURS, 2400, startDate, null, billingDay, true);
    }

    @ParameterizedTest(name = "startDate {0}, billingDay {1} → P1 {2}–{3}")
    @DisplayName("RN-211/RN-212: tabela normativa de geração da §6.2")
    @CsvSource({
        // startDate, billingDay, p1Start, p1End, p2Start, p2End, p3Start, p3End
        "2026-01-01, 1, 2026-01-01, 2026-01-31, 2026-02-01, 2026-02-28, 2026-03-01, 2026-03-31",
        "2026-01-10, 1, 2026-01-10, 2026-01-31, 2026-02-01, 2026-02-28, 2026-03-01, 2026-03-31",
        "2026-01-15, 15, 2026-01-15, 2026-02-14, 2026-02-15, 2026-03-14, 2026-03-15, 2026-04-14",
        "2026-01-20, 5, 2026-01-20, 2026-02-04, 2026-02-05, 2026-03-04, 2026-03-05, 2026-04-04",
        "2026-02-28, 28, 2026-02-28, 2026-03-27, 2026-03-28, 2026-04-27, 2026-04-28, 2026-05-27"
    })
    void shouldReproduceNormativeGenerationTable(
            LocalDate startDate,
            int billingDay,
            LocalDate p1Start,
            LocalDate p1End,
            LocalDate p2Start,
            LocalDate p2End,
            LocalDate p3Start,
            LocalDate p3End) {
        List<PeriodPlan> periods = generator.generate(monthly(startDate, billingDay), 3);

        assertThat(periods).hasSize(3);
        assertThat(periods.get(0).startDate()).isEqualTo(p1Start);
        assertThat(periods.get(0).endDate()).isEqualTo(p1End);
        assertThat(periods.get(1).startDate()).isEqualTo(p2Start);
        assertThat(periods.get(1).endDate()).isEqualTo(p2End);
        assertThat(periods.get(2).startDate()).isEqualTo(p3Start);
        assertThat(periods.get(2).endDate()).isEqualTo(p3End);
    }

    @Test
    @DisplayName("RN-217: exemplo normativo de rateio — 22 de 31 dias resulta em 1.703 minutos")
    void shouldProrateFirstPartialPeriod() {
        List<PeriodPlan> periods = generator.generate(monthly(LocalDate.of(2026, 1, 10), 1), 2);

        assertThat(periods.get(0).contractedMinutes()).isEqualTo(1703);
        assertThat(periods.get(0).partial()).isTrue();
        assertThat(periods.get(1).contractedMinutes()).isEqualTo(2400);
        assertThat(periods.get(1).partial()).isFalse();
    }

    @Test
    @DisplayName(
            "RN-211 passo 3 / CX-03: startDate igual ao billingDay produz ciclo cheio sem rateio")
    void shouldNotProrateWhenStartDateIsBillingDay() {
        List<PeriodPlan> periods = generator.generate(monthly(LocalDate.of(2026, 1, 15), 15), 1);

        assertThat(periods.get(0).endDate()).isEqualTo(LocalDate.of(2026, 2, 14));
        assertThat(periods.get(0).contractedMinutes()).isEqualTo(2400);
        assertThat(periods.get(0).partial()).isFalse();
    }

    @Test
    @DisplayName("CX-07: prorateFirstPeriod = false dá o valor cheio ao período parcial")
    void shouldSkipProrationWhenDisabled() {
        PeriodSpec spec =
                new PeriodSpec(
                        ContractType.MONTHLY_HOURS,
                        2400,
                        LocalDate.of(2026, 1, 10),
                        null,
                        1,
                        false);

        assertThat(generator.generate(spec, 1).get(0).contractedMinutes()).isEqualTo(2400);
    }

    @Test
    @DisplayName("RN-214/CX-05: endDate dentro de um período trunca e interrompe a geração")
    void shouldTruncateOnContractEndDate() {
        PeriodSpec spec =
                new PeriodSpec(
                        ContractType.MONTHLY_HOURS,
                        2400,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 10),
                        1,
                        true);

        List<PeriodPlan> periods = generator.generate(spec, 12);

        assertThat(periods).hasSize(2);
        assertThat(periods.get(1).endDate()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(periods.get(1).partial()).isTrue();
        // 10 de 28 dias do ciclo de fevereiro.
        assertThat(periods.get(1).contractedMinutes()).isEqualTo(857);
    }

    @Test
    @DisplayName("CX-04: endDate igual a startDate produz um único período de um dia")
    void shouldGenerateSingleDayPeriod() {
        LocalDate day = LocalDate.of(2026, 1, 10);
        PeriodSpec spec = new PeriodSpec(ContractType.MONTHLY_HOURS, 3000, day, day, 1, true);

        List<PeriodPlan> periods = generator.generate(spec, 5);

        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).startDate()).isEqualTo(day);
        assertThat(periods.get(0).endDate()).isEqualTo(day);
        assertThat(periods.get(0).contractedMinutes()).isEqualTo(97); // round(3000 × 1/31)
    }

    @Test
    @DisplayName("RN-210/CX-08: HOURLY_OPEN gera períodos com contractedMinutes zero")
    void shouldGenerateZeroMinutesForHourlyOpen() {
        PeriodSpec spec =
                new PeriodSpec(
                        ContractType.HOURLY_OPEN, null, LocalDate.of(2026, 1, 10), null, 1, true);

        List<PeriodPlan> periods = generator.generate(spec, 3);

        assertThat(periods).allSatisfy(period -> assertThat(period.contractedMinutes()).isZero());
        assertThat(periods.get(0).endDate()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    @DisplayName(
            "INV-PER-03: períodos são contíguos em 1.000 combinações de startDate × billingDay")
    void shouldKeepPeriodsContiguousAcrossCombinations() {
        LocalDate origin = LocalDate.of(2026, 1, 1);
        for (int dayOffset = 0; dayOffset < 40; dayOffset++) {
            for (int billingDay = 1; billingDay <= 28; billingDay++) {
                List<PeriodPlan> periods =
                        generator.generate(monthly(origin.plusDays(dayOffset), billingDay), 6);

                assertThat(periods).hasSize(6);
                for (int index = 0; index < periods.size(); index++) {
                    PeriodPlan period = periods.get(index);
                    // INV-PER-04.
                    assertThat(period.endDate()).isAfterOrEqualTo(period.startDate());
                    // INV-PER-01: sequência incremental a partir de 1.
                    assertThat(period.sequence()).isEqualTo(index + 1);
                    if (index > 0) {
                        // INV-PER-03: p[n].start = p[n−1].end + 1 dia — sem lacuna nem
                        // sobreposição.
                        assertThat(period.startDate())
                                .isEqualTo(periods.get(index - 1).endDate().plusDays(1));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("RN-212: períodos seguintes iniciam sempre no billingDay")
    void shouldStartSubsequentPeriodsOnBillingDay() {
        for (int day = 1; day <= 28; day++) {
            final int billingDay = day;
            List<PeriodPlan> periods =
                    generator.generate(monthly(LocalDate.of(2026, 1, 3), billingDay), 4);

            assertThat(periods.subList(1, periods.size()))
                    .allSatisfy(
                            period ->
                                    assertThat(period.startDate().getDayOfMonth())
                                            .isEqualTo(billingDay));
        }
    }

    @Test
    @DisplayName("CX-02: billingDay 28 em fevereiro de ano bissexto não recebe tratamento especial")
    void shouldHandleLeapFebruary() {
        List<PeriodPlan> periods = generator.generate(monthly(LocalDate.of(2028, 1, 28), 28), 2);

        assertThat(periods.get(0).endDate()).isEqualTo(LocalDate.of(2028, 2, 27));
        assertThat(periods.get(1).startDate()).isEqualTo(LocalDate.of(2028, 2, 28));
        assertThat(periods.get(1).endDate()).isEqualTo(LocalDate.of(2028, 3, 27));
    }

    @Test
    @DisplayName("CE-ME-09: a geração após um período existente preserva a contiguidade")
    void shouldGenerateFollowingPeriodsAfterGap() {
        PeriodSpec spec = monthly(LocalDate.of(2026, 1, 1), 1);

        List<PeriodPlan> periods = generator.generateAfter(spec, LocalDate.of(2026, 3, 31), 3, 2);

        assertThat(periods).hasSize(2);
        assertThat(periods.get(0).sequence()).isEqualTo(4);
        assertThat(periods.get(0).startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(periods.get(1).startDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName("entities.md §6.7: o label deriva das datas do período")
    void shouldDeriveLabelFromDates() {
        assertThat(generator.generate(monthly(LocalDate.of(2026, 7, 1), 1), 1).get(0).label())
                .isEqualTo("2026-07");
        assertThat(generator.generate(monthly(LocalDate.of(2026, 7, 15), 15), 1).get(0).label())
                .isEqualTo("2026-07-15 a 2026-08-14");
    }

    @Test
    @DisplayName("BR-150: a geração é determinística em execuções repetidas")
    void shouldBeDeterministic() {
        PeriodSpec spec = monthly(LocalDate.of(2026, 1, 10), 1);
        List<PeriodPlan> first = generator.generate(spec, 6);

        for (int repetition = 0; repetition < 20; repetition++) {
            assertThat(generator.generate(spec, 6)).isEqualTo(first);
        }
    }
}
