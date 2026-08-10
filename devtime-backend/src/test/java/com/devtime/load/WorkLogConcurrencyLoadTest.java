package com.devtime.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.support.WorkLogScenario;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T-008-36: RN-102 sob concorrência.
 *
 * <p>A suíte comum prova que {@code OverlapDetector} rejeita uma sobreposição <b>já persistida</b>.
 * Ela não prova nada sobre duas escritas simultâneas: entre a verificação e a inserção existe uma
 * janela, e é dentro dela que o registro duplicado nasce. RP-01 classifica hora contada duas vezes
 * como falha crítica — é o número que vai para a fatura do cliente.
 *
 * <p>O teste dispara N tentativas <b>no mesmo instante</b> para o mesmo intervalo e verifica
 * quantos registros sobreviveram. Uma única falha aqui significa cobrança indevida em produção.
 */
class WorkLogConcurrencyLoadTest extends LoadTestSupport {

    private static final int TENTATIVAS_SIMULTANEAS = 16;

    @Autowired private WorkLogService workLogService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("T-008-36: 16 criações simultâneas do mesmo intervalo produzem no máximo uma")
    void concurrentOverlappingCreatesMustNotDuplicate() throws Exception {
        var setup = asOwnerOfA(scenario::create);
        Instant comeco = WorkLogScenario.at(9, 0);
        Instant fim = WorkLogScenario.at(11, 0);

        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger aceitos = new AtomicInteger();
        AtomicInteger recusados = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(TENTATIVAS_SIMULTANEAS)) {
            List<Future<Object>> tentativas =
                    java.util.stream.IntStream.range(0, TENTATIVAS_SIMULTANEAS)
                            .mapToObj(
                                    indice ->
                                            pool.submit(
                                                    () -> {
                                                        largada.await();
                                                        try {
                                                            asOwnerOfA(
                                                                    () ->
                                                                            workLogService.create(
                                                                                    request(
                                                                                            setup,
                                                                                            comeco,
                                                                                            fim)));
                                                            aceitos.incrementAndGet();
                                                        } catch (RuntimeException recusa) {
                                                            // RN-102 rejeitando é o resultado
                                                            // desejado em 15 das 16 tentativas.
                                                            recusados.incrementAndGet();
                                                        }
                                                        return null;
                                                    }))
                            .toList();

            largada.countDown();
            for (Future<Object> tentativa : tentativas) {
                tentativa.get(60, TimeUnit.SECONDS);
            }
        }

        Long persistidos =
                jdbc().queryForObject(
                                """
                        SELECT count(*) FROM work_logs
                         WHERE tenant_id = ? AND deleted_at IS NULL
                           AND started_at = ? AND ended_at = ?
                        """,
                                Long.class,
                                tenantAId,
                                java.sql.Timestamp.from(comeco),
                                java.sql.Timestamp.from(fim));

        System.out.println(
                "T-008-36 — aceitos="
                        + aceitos.get()
                        + " recusados="
                        + recusados.get()
                        + " persistidos="
                        + persistidos);

        assertThat(persistidos)
                .as("RN-102 / RP-01: duas horas contadas duas vezes é cobrança indevida")
                .isEqualTo(1L);
        assertThat(aceitos.get()).isEqualTo(1);
        assertThat(recusados.get()).isEqualTo(TENTATIVAS_SIMULTANEAS - 1);
    }

    private WorkLogCreateRequest request(
            WorkLogScenario.Scenario setup, Instant comeco, Instant fim) {
        return new WorkLogCreateRequest(
                setup.ticket().id(),
                comeco,
                fim,
                0,
                "Registro concorrente do mesmo intervalo",
                setup.category().id(),
                true,
                List.of(),
                null);
    }
}
