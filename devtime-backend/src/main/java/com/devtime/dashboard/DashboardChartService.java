package com.devtime.dashboard;

import com.devtime.dashboard.domain.ChartType;
import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.dashboard.dto.DashboardResponses.ChartResponse;
import com.devtime.dashboard.dto.DashboardResponses.DashboardChartsDto;
import com.devtime.shared.time.DateRange;
import java.time.LocalDate;

/**
 * Os seis gráficos do painel (§10.2 de reports.md, §22.2 de specs/010).
 *
 * <p>Separado de {@link DashboardService} porque a recarga isolada de um gráfico (FA-07) é um
 * caminho próprio: trocar o período de um gráfico não deve recarregar cartões nem estatísticas.
 */
public interface DashboardChartService {

    /**
     * Um gráfico isolado, no período pedido.
     *
     * @param type nome externo em kebab-case, um dos seis de §10.2
     */
    ChartResponse chart(String type, DashboardPeriodType period, LocalDate from, LocalDate to);

    /**
     * Os três gráficos da resposta principal (§10.1).
     *
     * <p>Recebe o intervalo já resolvido para que a resposta completa e cada gráfico isolado
     * enxerguem exatamente o mesmo período — resolvê-lo duas vezes permitiria divergência na virada
     * do dia.
     */
    DashboardChartsDto mainCharts(DateRange range);

    /** Um gráfico isolado, com o intervalo já resolvido. */
    ChartResponse chart(ChartType type, DateRange range);
}
