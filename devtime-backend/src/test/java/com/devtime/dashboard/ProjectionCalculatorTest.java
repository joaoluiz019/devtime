package com.devtime.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.dashboard.domain.ProjectionStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Projeção de consumo (§6.3 de specs/010, entities.md §6.7).
 *
 * <p>R-04: a projeção instável é o risco desta parte — "vai estourar" no dia 2 e "tudo certo" no
 * dia 10 faz o usuário parar de olhar para ela. A guarda de 3 dias úteis é o que impede isso, e é o
 * comportamento mais testado aqui.
 *
 * <p>Todas as datas são de julho de 2026: 01/07 é uma quarta-feira, o que torna a contagem de dias
 * úteis verificável à mão.
 */
class ProjectionCalculatorTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 7, 31);

    private final ProjectionCalculator calculator = new ProjectionCalculator();

    @Test
    @DisplayName("§6.3: julho de 2026 tem 23 dias úteis de segunda a sexta")
    void workDaysOfReferenceMonth() {
        assertThat(calculator.workDaysBetween(PERIOD_START, PERIOD_END)).isEqualTo(23);
    }

    @Test
    @DisplayName("CX-04 / CA-06: menos de 3 dias úteis decorridos produz NOT_APPLICABLE")
    void guardOfThreeWorkDays() {
        // 02/07 é quinta: 01 e 02 são dois dias úteis decorridos.
        ProjectionCalculator.Projection projection =
                calculator.calculate(PERIOD_START, PERIOD_END, LocalDate.of(2026, 7, 2), 600, 2400);

        assertThat(projection.status()).isEqualTo(ProjectionStatus.NOT_APPLICABLE);
        assertThat(projection.projectedConsumedMinutes())
                .as("sem base estatística, nenhum número é exibido")
                .isZero();
    }

    @Test
    @DisplayName("§6.3: com exatamente 3 dias úteis a projeção passa a ser exibida")
    void thirdWorkDayEnablesProjection() {
        // 03/07 é sexta: 01, 02 e 03 são três dias úteis.
        ProjectionCalculator.Projection projection =
                calculator.calculate(PERIOD_START, PERIOD_END, LocalDate.of(2026, 7, 3), 600, 6000);

        assertThat(projection.status()).isEqualTo(ProjectionStatus.WITHIN_LIMIT);
        // burnRate = 600/3 = 200; projeção = 200 × 23 = 4600.
        assertThat(projection.projectedConsumedMinutes()).isEqualTo(4600);
    }

    @Test
    @DisplayName("§6.3: projeção dentro do disponível é WITHIN_LIMIT")
    void withinLimit() {
        assertThat(
                        calculator
                                .calculate(
                                        PERIOD_START,
                                        PERIOD_END,
                                        LocalDate.of(2026, 7, 10),
                                        1000,
                                        10000)
                                .status())
                .isEqualTo(ProjectionStatus.WITHIN_LIMIT);
    }

    @Test
    @DisplayName("§6.3: estouro dentro de 10% de margem é AT_RISK")
    void atRisk() {
        // 10/07 é sexta: 8 dias úteis decorridos. burnRate = 4000/8 = 500; projeção = 500 × 23 =
        // 11.500, que está entre 11.000 e 11.000 × 1,1 = 12.100.
        ProjectionCalculator.Projection projection =
                calculator.calculate(
                        PERIOD_START, PERIOD_END, LocalDate.of(2026, 7, 10), 4000, 11000);

        assertThat(projection.projectedConsumedMinutes()).isEqualTo(11500);
        assertThat(projection.status()).isEqualTo(ProjectionStatus.AT_RISK);
    }

    @Test
    @DisplayName("§6.3: estouro além de 10% de margem é WILL_EXCEED")
    void willExceed() {
        ProjectionCalculator.Projection projection =
                calculator.calculate(
                        PERIOD_START, PERIOD_END, LocalDate.of(2026, 7, 10), 4000, 5000);

        assertThat(projection.projectedConsumedMinutes()).isEqualTo(11500);
        assertThat(projection.status()).isEqualTo(ProjectionStatus.WILL_EXCEED);
    }

    @Test
    @DisplayName("CX-05 / CE-10: saldo disponível zero (HOURLY_OPEN) não tem projeção")
    void hourlyOpenHasNoProjection() {
        assertThat(
                        calculator
                                .calculate(
                                        PERIOD_START, PERIOD_END, LocalDate.of(2026, 7, 20), 900, 0)
                                .status())
                .isEqualTo(ProjectionStatus.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("BR-150: hoje após o fim do período não infla o número de dias decorridos")
    void elapsedIsClampedToPeriodEnd() {
        ProjectionCalculator.Projection afterEnd =
                calculator.calculate(
                        PERIOD_START, PERIOD_END, LocalDate.of(2026, 8, 15), 4600, 10000);
        ProjectionCalculator.Projection atEnd =
                calculator.calculate(PERIOD_START, PERIOD_END, PERIOD_END, 4600, 10000);

        assertThat(afterEnd).isEqualTo(atEnd);
        // Período inteiro decorrido: a projeção coincide com o consumo real.
        assertThat(afterEnd.projectedConsumedMinutes()).isEqualTo(4600);
    }

    @Test
    @DisplayName("BR-145: a projeção é truncada, nunca arredondada para cima")
    void projectionIsTruncated() {
        // burnRate = 1000/8 = 125; 125 × 23 = 2875 exato. Com 1001 minutos, 125,125 × 23 = 2877,875
        // e o truncamento produz 2877.
        ProjectionCalculator.Projection projection =
                calculator.calculate(
                        PERIOD_START, PERIOD_END, LocalDate.of(2026, 7, 10), 1001, 10000);

        assertThat(projection.projectedConsumedMinutes()).isEqualTo(2877);
    }

    @Test
    @DisplayName("§6.3: período sem datas resolvidas não produz projeção em vez de falhar")
    void missingPeriodDatesAreNotApplicable() {
        assertThat(calculator.calculate(null, null, PERIOD_START, 100, 1000).status())
                .isEqualTo(ProjectionStatus.NOT_APPLICABLE);
    }
}
