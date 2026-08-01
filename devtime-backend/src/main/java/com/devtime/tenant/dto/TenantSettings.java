package com.devtime.tenant.dto;

import java.util.List;

/**
 * Preferências operacionais do tenant, já tipadas (entities.md §6.1.1).
 *
 * <p>A coluna é {@code JSONB} para permitir evolução sem migration, mas quem consome uma regra de
 * negócio precisa de um {@code int}, não de um {@code Map<String, Object>}: {@code roundingMinutes}
 * decide quanto o cliente é cobrado (RN-113) e {@code retroactiveLimitDays} decide quem pode lançar
 * horas de meses atrás (RN-120). Fazer cada feature ler o mapa e converter o valor espalharia a
 * conversão — e o valor padrão — por todo o código.
 *
 * <p>Os defaults são aplicados aqui, no servidor, e não assumidos pelo consumidor: um tenant criado
 * antes da introdução de uma chave a receberia ausente, e cada chamador teria de conhecer o padrão.
 *
 * @param workDayMinutes jornada de referência para métricas
 * @param workDays dias úteis em numeração ISO (1 = segunda)
 * @param defaultRolloverPolicy pré-preenchimento ao criar contrato
 * @param defaultOveragePolicy pré-preenchimento ao criar contrato
 * @param timerLongRunningMinutes RN-163 — limiar de alerta de cronômetro longo
 * @param timerAutoAbandonMinutes RN-164 — limiar de marcação como abandonado
 * @param allowFutureWorkLogs RN-119 — padrão conservador: falso
 * @param retroactiveLimitDays RN-120 — janela de lançamento retroativo
 * @param roundingMinutes RN-113 — {@code 0} desativa; o arredondamento é sempre para baixo
 * @param notificationThresholds RN-602 — percentuais de alerta de consumo
 */
public record TenantSettings(
        int workDayMinutes,
        List<Integer> workDays,
        String defaultRolloverPolicy,
        String defaultOveragePolicy,
        int timerLongRunningMinutes,
        int timerAutoAbandonMinutes,
        boolean allowFutureWorkLogs,
        int retroactiveLimitDays,
        int roundingMinutes,
        List<Integer> notificationThresholds) {

    /** Valores de entities.md §6.1.1, usados para toda chave ausente. */
    public static TenantSettings defaults() {
        return new TenantSettings(
                480,
                List.of(1, 2, 3, 4, 5),
                "NONE",
                "WARN",
                480,
                960,
                false,
                30,
                0,
                List.of(50, 80, 100));
    }
}
