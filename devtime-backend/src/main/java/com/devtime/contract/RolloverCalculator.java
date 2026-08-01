package com.devtime.contract;

import com.devtime.contract.domain.RolloverPolicy;
import org.springframework.stereotype.Component;

/**
 * Transporte de saldo entre períodos (RN-224 a RN-228, §6.2 de specs/011).
 *
 * <p>BR-067: uma estratégia por valor do enum, sem condicional espalhada pelo serviço de
 * fechamento. BR-066: cálculo puro e determinístico.
 *
 * <p><b>Saldo negativo nunca é transportado</b> (RN-228). Transportar dívida transformaria um
 * problema pontual em permanente e tornaria o saldo incompreensível para o cliente: excedente é uma
 * negociação do mês, não uma pendência acumulada. As três políticas partem de {@code max(0,
 * remaining)} justamente por isso.
 */
@Component
public class RolloverCalculator {

    /**
     * RN-224: calculado <b>apenas no fechamento</b>.
     *
     * @param remainingMinutes RN-220, podendo ser negativo
     * @param capMinutes teto de {@code CAPPED}; nulo equivale a zero
     * @return minutos a transportar para o período seguinte, sempre {@code >= 0}
     */
    public int carriedOut(RolloverPolicy policy, int remainingMinutes, Integer capMinutes) {
        // RN-228: o piso em zero é aplicado antes de qualquer política.
        int positiveRemaining = Math.max(0, remainingMinutes);
        return switch (policy) {
            // RN-225: o saldo positivo é perdido. É uma decisão comercial legítima, não um defeito.
            case NONE -> 0;
            // RN-226.
            case FULL -> positiveRemaining;
            // RN-227. CX-05: teto zero equivale a NONE — aceito e documentado.
            case CAPPED -> Math.min(positiveRemaining, capMinutes == null ? 0 : capMinutes);
        };
    }
}
