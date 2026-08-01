package com.devtime.contract.domain;

import java.math.BigDecimal;

/**
 * Resultado das fórmulas canônicas de saldo (RN-218 a RN-222).
 *
 * <p>Value object puro: nasce de {@code BalanceCalculator} e não conhece persistência. Existe como
 * tipo próprio, e não como campos soltos, porque a <b>ordem de cálculo é obrigatória</b> — {@code
 * available} → {@code consumed} → {@code remaining} → {@code overage} → {@code rate} — e cada valor
 * depende do anterior. Devolvê-los juntos impede que um chamador calcule um deles fora de ordem e
 * obtenha um resultado inconsistente quando {@code available} é zero.
 *
 * @param remainingMinutes RN-220 — <b>pode ser negativo</b>; é o excedente visto por outro ângulo
 * @param consumptionRate RN-222 — percentual com 2 casas em {@link BigDecimal}; ponto flutuante
 *     binário produziria {@code 105.06999999} onde o cliente espera {@code 105,07}
 */
public record PeriodBalance(
        int contractedMinutes,
        int carriedInMinutes,
        int adjustmentMinutes,
        int availableMinutes,
        int consumedMinutes,
        int nonBillableMinutes,
        int remainingMinutes,
        int overageMinutes,
        BigDecimal consumptionRate) {}
