package com.devtime.dashboard.domain;

/**
 * Leitura da projeção de consumo (§6.3 de specs/010).
 *
 * <p>Baseada em {@code projectedConsumption = burnRate × totalWorkDays} (entities.md §6.7).
 */
public enum ProjectionStatus {

    /** {@code projectedConsumption ≤ available}. */
    WITHIN_LIMIT,

    /**
     * {@code available < projectedConsumption ≤ available × 1,1}: estouro provável, margem
     * estreita.
     */
    AT_RISK,

    /** {@code projectedConsumption > available × 1,1}. */
    WILL_EXCEED,

    /**
     * Sem base estatística: {@code available = 0} ou menos de 3 dias úteis decorridos.
     *
     * <p>A guarda de 3 dias é deliberada (OB-04): com um único dia decorrido, {@code burnRate ×
     * totalWorkDays} projeta cerca de 20× esse dia — matematicamente correto e praticamente inútil.
     * Exibir "vai estourar" no dia 2 e "tudo certo" no dia 10 destrói a confiança na projeção.
     */
    NOT_APPLICABLE
}
