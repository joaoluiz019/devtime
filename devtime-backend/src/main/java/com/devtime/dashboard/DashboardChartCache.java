package com.devtime.dashboard;

import com.devtime.dashboard.domain.ChartType;
import com.devtime.dashboard.domain.DashboardScope;
import com.devtime.dashboard.dto.DashboardResponses.ChartResponse;
import com.devtime.shared.time.TenantClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cache dos gráficos do painel (§20 de specs/010).
 *
 * <p>Apenas os gráficos são cacheados. {@code quickStats} e os cartões de contrato não são: são os
 * valores que o usuário mais espera ver atualizados no instante em que registra horas, e já são
 * rápidos por serem servidos de campos desnormalizados.
 *
 * <p><b>SG-07 / CP-10: a chave sempre inclui {@code tenantId} e o escopo resolvido</b>, e o {@code
 * userId} quando o escopo é {@code USER}. Sem isso, o painel de um tenant responderia com o gráfico
 * de outro — a falha mais grave prevista para a feature (R-05).
 *
 * <p>Implementado sobre {@link ConcurrentHashMap} e não sobre uma biblioteca de cache porque a
 * necessidade é exatamente esta: expiração por tempo e invalidação por tenant. Acrescentar uma
 * dependência exigiria a justificativa de DP-01 para substituir vinte linhas.
 *
 * <p><b>Dívida conhecida (OB-09):</b> o cache é local à instância. Com múltiplas instâncias cada
 * uma mantém o seu, e a invalidação por evento só alcança a que a recebeu. Enquanto o deploy for de
 * instância única (§10 de architecture.md) isso é irrelevante; ao escalar horizontalmente, o
 * caminho é um cache distribuído com invalidação por canal — mudança de infraestrutura, sem
 * alteração de lógica.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardChartCache {

    /** §20: cinco minutos. O painel é uma foto do momento, não um monitor em tempo real. */
    static final Duration TTL = Duration.ofMinutes(5);

    private final TenantClock clock;
    private final DashboardMetrics metrics;

    private final Map<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();

    /**
     * Chave do cache.
     *
     * @param userId nulo no escopo {@code TENANT}; preenchido no escopo {@code USER}, sem o que
     *     dois membros do mesmo tenant compartilhariam o gráfico um do outro
     */
    record CacheKey(
            UUID tenantId,
            DashboardScope scope,
            UUID userId,
            ChartType chartType,
            String periodKey) {}

    private record CacheEntry(ChartResponse value, Instant expiresAt) {}

    /** Devolve o valor em cache ou o produz e armazena. */
    public ChartResponse get(CacheKey key, Supplier<ChartResponse> loader) {
        Instant now = clock.now();
        CacheEntry cached = entries.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            // §28: chave sem dados. O conteúdo do cache nunca entra em log.
            log.debug(
                    "cache de gráfico: acerto chartType={} scope={}", key.chartType(), key.scope());
            metrics.recordChartCache(key.chartType().getExternalName(), true);
            return cached.value();
        }
        log.debug("cache de gráfico: falha chartType={} scope={}", key.chartType(), key.scope());
        metrics.recordChartCache(key.chartType().getExternalName(), false);
        ChartResponse value = loader.get();
        entries.put(key, new CacheEntry(value, now.plus(TTL)));
        return value;
    }

    /**
     * Invalida tudo o que pertence ao tenant (§15).
     *
     * <p>Por tenant e não por chave exata: um registro de horas altera a série diária, a
     * distribuição por cliente, por categoria e por contrato de uma só vez, e enumerar as chaves
     * afetadas seria mais frágil que descartar o conjunto — que é pequeno e barato de reconstruir.
     */
    public void invalidateTenant(UUID tenantId) {
        entries.keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }

    /** Descarta as entradas vencidas; o mapa cresceria indefinidamente sem isso. */
    public void evictExpired() {
        Instant now = clock.now();
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    int size() {
        return entries.size();
    }
}
