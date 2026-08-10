package com.devtime.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.support.WorkLogScenario;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogFilter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * T-008-43: consulta de registro de horas com 100.000 registros no tenant.
 *
 * <p>O que está sob teste não é a velocidade da máquina, e sim se os índices de V016 estão sendo
 * <b>usados</b>. Um teste de tempo absoluto em runner compartilhado é instável; por isso a asserção
 * principal é sobre o plano de execução — uma varredura sequencial em {@code work_logs} com este
 * volume significa que o índice deixou de servir à consulta, e é isso que degrada em produção.
 */
class WorkLogVolumeLoadTest extends LoadTestSupport {

    @Autowired private WorkLogService workLogService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("T-008-43: a listagem paginada não varre a tabela com 100.000 registros")
    void pagedSearchMustUseIndexes() {
        var setup = asOwnerOfA(scenario::create);
        seedWorkLogs(setup, userAId, VOLUME);

        assertThat(
                        jdbc().queryForObject(
                                        "SELECT count(*) FROM work_logs WHERE tenant_id = ?",
                                        Long.class,
                                        tenantAId))
                .isEqualTo(VOLUME);

        jdbc().execute("ANALYZE work_logs");

        String plano =
                String.join(
                        "\n",
                        jdbc().queryForList(
                                        """
                        EXPLAIN SELECT * FROM work_logs
                         WHERE tenant_id = ? AND deleted_at IS NULL
                         ORDER BY work_date DESC, started_at DESC
                         LIMIT 50
                        """,
                                        String.class,
                                        tenantAId));

        assertThat(plano)
                .as("QY-03: com 100.000 registros, varrer a tabela para devolver 50 é o defeito")
                .doesNotContain("Seq Scan on work_logs");

        List<Duration> duracoes =
                medir(
                        20,
                        () ->
                                asOwnerOfA(
                                        () ->
                                                workLogService.search(
                                                        WorkLogFilter.empty(),
                                                        PageRequest.of(0, 50))));

        Duration p95 = percentil(duracoes, 95);
        System.out.println("T-008-43 — p95 da listagem com " + VOLUME + " registros: " + p95);
        assertThat(p95)
                .as("FR-166: a listagem responde abaixo de 1s no percentil 95")
                .isLessThan(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("T-008-43: a contagem por filtro também usa índice")
    void filteredCountMustUseIndex() {
        var setup = asOwnerOfA(scenario::create);
        seedWorkLogs(setup, userAId, VOLUME);
        jdbc().execute("ANALYZE work_logs");

        String plano =
                String.join(
                        "\n",
                        jdbc().queryForList(
                                        """
                        EXPLAIN SELECT count(*) FROM work_logs
                         WHERE tenant_id = ? AND contract_period_id = ? AND deleted_at IS NULL
                        """,
                                        String.class,
                                        tenantAId,
                                        setup.period().id()));

        assertThat(plano).doesNotContain("Seq Scan on work_logs");
    }
}
