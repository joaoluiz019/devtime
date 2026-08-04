package com.devtime.dashboard;

import com.devtime.dashboard.dto.DashboardResponses.ChartPointDto;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCalendarDay;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Preenche as lacunas da série diária (CP-04, INV-DSH-03 de specs/010).
 *
 * <p>Um gráfico de barras que omite os dias sem registro comprime o eixo e sugere trabalho contínuo
 * onde houve pausa. Os zeros são informação: é neles que o usuário vê que parou.
 */
@Component
public class ChartGapFiller {

    /** RS-04: a série tem exatamente 30 pontos, sempre. */
    public static final int SERIES_LENGTH = 30;

    /**
     * Série de exatamente {@link #SERIES_LENGTH} pontos terminando em {@code lastDay}.
     *
     * <p>A janela é sempre a dos 30 dias que terminam no fim do intervalo consultado, e não o
     * intervalo inteiro: RS-04 fixa o número de pontos, e um {@code CUSTOM} de 366 dias produziria
     * 366 barras ilegíveis. Um intervalo mais curto que 30 dias é completado com zeros à esquerda,
     * pelo mesmo motivo que os dias vazios internos aparecem.
     *
     * @param aggregated dias com registro, em qualquer ordem; dias fora da janela são ignorados
     */
    public List<ChartPointDto> fill(List<WorkLogCalendarDay> aggregated, LocalDate lastDay) {
        Map<LocalDate, WorkLogCalendarDay> byDate =
                aggregated.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        WorkLogCalendarDay::date,
                                        Function.identity(),
                                        (first, second) -> first));

        LocalDate firstDay = lastDay.minusDays(SERIES_LENGTH - 1L);
        return java.util.stream.IntStream.range(0, SERIES_LENGTH)
                .mapToObj(offset -> firstDay.plusDays(offset))
                .map(
                        date -> {
                            WorkLogCalendarDay day = byDate.get(date);
                            return day == null
                                    ? new ChartPointDto(date, 0, 0)
                                    : new ChartPointDto(
                                            date, day.totalMinutes(), day.billableMinutes());
                        })
                .toList();
    }

    /**
     * Primeiro dia da janela de 30 pontos, para que a consulta agregue apenas o necessário.
     *
     * <p>Agregar o intervalo inteiro e descartar o excedente leria até 366 dias para exibir 30.
     */
    public LocalDate firstDayOfSeries(LocalDate lastDay) {
        return lastDay.minusDays(SERIES_LENGTH - 1L);
    }
}
