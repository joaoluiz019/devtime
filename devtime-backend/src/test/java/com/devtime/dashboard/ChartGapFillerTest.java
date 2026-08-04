package com.devtime.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.dashboard.dto.DashboardResponses.ChartPointDto;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCalendarDay;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Série diária sempre completa (CP-04, INV-DSH-03, R-08 de specs/010).
 *
 * <p>Um gráfico de barras que omite os dias sem registro comprime o eixo e sugere trabalho contínuo
 * onde houve pausa. Os zeros são informação.
 */
class ChartGapFillerTest {

    private static final LocalDate LAST_DAY = LocalDate.of(2026, 7, 31);

    private final ChartGapFiller filler = new ChartGapFiller();

    @Test
    @DisplayName("CA-05 / RS-04: a série tem exatamente 30 pontos, terminando no último dia")
    void alwaysThirtyPoints() {
        List<ChartPointDto> points = filler.fill(List.of(), LAST_DAY);

        assertThat(points).hasSize(30);
        assertThat(points.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(points.get(29).date()).isEqualTo(LAST_DAY);
    }

    @Test
    @DisplayName("CX-02 / FA-02: mês sem nenhum registro produz 30 pontos em zero, não lista vazia")
    void emptyMonthIsThirtyZeros() {
        assertThat(filler.fill(List.of(), LAST_DAY))
                .allSatisfy(
                        point -> {
                            assertThat(point.netMinutes()).isZero();
                            assertThat(point.billableMinutes()).isZero();
                        });
    }

    @Test
    @DisplayName("CX-03: um único dia com registro produz 29 zeros e 1 com valor")
    void singleDayWithWork() {
        LocalDate worked = LocalDate.of(2026, 7, 15);
        List<ChartPointDto> points =
                filler.fill(List.of(new WorkLogCalendarDay(worked, 480, 450, 2)), LAST_DAY);

        assertThat(points).hasSize(30);
        assertThat(points).filteredOn(point -> point.netMinutes() > 0).hasSize(1);
        assertThat(points)
                .filteredOn(point -> point.date().equals(worked))
                .singleElement()
                .satisfies(
                        point -> {
                            assertThat(point.netMinutes()).isEqualTo(480);
                            assertThat(point.billableMinutes()).isEqualTo(450);
                        });
    }

    @Test
    @DisplayName("CP-04: dias agregados fora da janela de 30 pontos são descartados")
    void daysOutsideWindowAreIgnored() {
        List<ChartPointDto> points =
                filler.fill(
                        List.of(new WorkLogCalendarDay(LocalDate.of(2026, 6, 1), 300, 300, 1)),
                        LAST_DAY);

        assertThat(points).hasSize(30);
        assertThat(points).allSatisfy(point -> assertThat(point.netMinutes()).isZero());
    }

    @Test
    @DisplayName("§13.4: a janela consultada começa 29 dias antes do último dia")
    void firstDayOfSeries() {
        assertThat(filler.firstDayOfSeries(LAST_DAY)).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    @DisplayName("CX-20: a série atravessa a virada do mês sem duplicar nem omitir dias")
    void crossesMonthBoundary() {
        List<ChartPointDto> points = filler.fill(List.of(), LocalDate.of(2026, 3, 10));

        assertThat(points).hasSize(30);
        assertThat(points.get(0).date()).isEqualTo(LocalDate.of(2026, 2, 9));
        assertThat(points).extracting(ChartPointDto::date).doesNotHaveDuplicates();
    }
}
