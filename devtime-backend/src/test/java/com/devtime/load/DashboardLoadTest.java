package com.devtime.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.dashboard.DashboardService;
import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.support.WorkLogScenario;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T-010-24 / RP-06: o painel responde abaixo de 800 ms no percentil 95 com 100.000 registros.
 *
 * <p>É o teste que o índice coberto de V031 existe para sustentar. O painel é a primeira tela de
 * toda sessão: se ele degrada com volume, o produto parece lento inteiro, independentemente do
 * resto.
 *
 * <p>A primeira execução é descartada. O cache de 5 minutos de {@code 010} responderia as demais em
 * microssegundos e o teste mediria o cache, não a agregação — por isso as medições usam intervalos
 * distintos, que produzem chaves de cache distintas.
 */
class DashboardLoadTest extends LoadTestSupport {

    private static final Duration META_P95 = Duration.ofMillis(800);

    @Autowired private DashboardService dashboardService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("T-010-24: p95 do painel abaixo de 800 ms com 100.000 registros")
    void dashboardMustStayUnderTargetWithVolume() {
        var setup = asOwnerOfA(scenario::create);
        seedWorkLogs(setup, userAId, VOLUME);
        jdbc().execute("ANALYZE work_logs");

        LocalDate inicio = setup.period().startDate();
        LocalDate fim = setup.period().endDate();

        // Aquecimento: a primeira consulta paga o plano e o carregamento de páginas do índice.
        asOwnerOfA(() -> dashboardService.load(DashboardPeriodType.CUSTOM, inicio, fim));

        List<Duration> duracoes =
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(
                                volta -> {
                                    // Data final distinta a cada volta: mesma massa de dados, chave
                                    // de cache diferente. Sem isso o teste mediria o cache.
                                    LocalDate fimDaVolta = fim.minusDays(volta % 5);
                                    java.time.Instant comeco = java.time.Instant.now();
                                    asOwnerOfA(
                                            () ->
                                                    dashboardService.load(
                                                            DashboardPeriodType.CUSTOM,
                                                            inicio,
                                                            fimDaVolta));
                                    return Duration.between(comeco, java.time.Instant.now());
                                })
                        .toList();

        Duration p95 = percentil(duracoes, 95);
        System.out.println("T-010-24 — p95 do painel com " + VOLUME + " registros: " + p95);

        assertThat(p95).as("RP-06 / FR-166: meta de 800 ms no percentil 95").isLessThan(META_P95);
    }
}
