package com.devtime.dashboard;

import com.devtime.dashboard.domain.ContractSeverity;
import com.devtime.dashboard.domain.DashboardScope;
import com.devtime.dashboard.dto.DashboardResponses.ContractStatusDto;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Métricas do painel (§29 de specs/010).
 *
 * <p>A que carrega alerta é {@code dashboard.load.duration}: <b>p95 acima de 800 ms</b> é a meta
 * RNF-003 e o gatilho do risco central da feature (RP-06). As demais existem para localizar a causa
 * quando ela dispara — {@code dashboard.block.duration} identifica o bloco lento e {@code
 * dashboard.contracts.count} diz se o volume de cartões justifica revisar a paginação.
 */
@Component
@RequiredArgsConstructor
public class DashboardMetrics {

    private final MeterRegistry registry;

    /** Percentual de contratos em {@code CRITICAL}, publicado como medidor observável. */
    private final AtomicInteger criticalContracts = new AtomicInteger();

    /** {@code dashboard.load.duration} com tag {@code scope}. Alerta em p95 &gt; 800 ms. */
    public void recordLoad(DashboardScope scope, long durationNanos) {
        registry.timer("dashboard.load.duration", "scope", scope.name())
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /** {@code dashboard.block.duration} com tag {@code block}. */
    public void recordBlock(String block, long durationNanos) {
        registry.timer("dashboard.block.duration", "block", block)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /** {@code dashboard.block.failed}: acima de 1% das cargas indica instabilidade. */
    public void recordBlockFailure(String block) {
        registry.counter("dashboard.block.failed", "block", block).increment();
    }

    /**
     * {@code dashboard.range.rejected}: crescimento indica seletor permitindo intervalo inválido.
     */
    public void recordRejectedRange() {
        registry.counter("dashboard.range.rejected").increment();
    }

    /**
     * {@code dashboard.contracts.count} e {@code dashboard.severity.critical}.
     *
     * <p>O medidor de críticos é registrado uma única vez, na primeira carga, e depois apenas
     * atualizado: registrar a cada requisição criaria um medidor por chamada.
     */
    public void recordContracts(List<ContractStatusDto> contracts) {
        registry.summary("dashboard.contracts.count").record(contracts.size());
        criticalContracts.set(
                (int)
                        contracts.stream()
                                .filter(
                                        contract ->
                                                contract.severity() == ContractSeverity.CRITICAL)
                                .count());
        registry.gauge("dashboard.severity.critical", criticalContracts);
    }

    /** {@code dashboard.chart.cache_hit_ratio}: abaixo de 50% indica TTL inadequado. */
    public void recordChartCache(String chartType, boolean hit) {
        registry.counter(
                        "dashboard.chart.cache",
                        "chartType",
                        chartType,
                        "outcome",
                        hit ? "hit" : "miss")
                .increment();
    }
}
