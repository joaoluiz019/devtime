package com.devtime.contract;

import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodBalance;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Fórmulas canônicas do banco de horas (RN-218 a RN-223, §6.1 de specs/011).
 *
 * <p>BR-066: cálculo puro, determinístico (BR-150) e sem efeito colateral. É <b>a</b> aritmética do
 * produto: o saldo é o número que o cliente pergunta, o que sustenta a fatura e o que aparece no
 * topo do painel. RP-03 classifica erro de cálculo de saldo como risco crítico, e SQ-10 determina
 * que uma divergência reportada bloqueia toda a fila de desenvolvimento.
 *
 * <p><b>A ordem de cálculo é obrigatória</b> (§6.1): {@code available} → {@code consumed} → {@code
 * remaining} → {@code overage} → {@code rate}. Cada valor depende do anterior, e calcular fora de
 * ordem produz resultado inconsistente quando {@code available} é zero.
 *
 * <p>Toda a aritmética é inteira (RN-010), exceto {@code consumptionRate} — o único valor
 * fracionário — que usa {@link BigDecimal} com arredondamento explícito. {@code double} produziria
 * {@code 105.06999999} em vez de {@code 105,07}, e o número exibido ao cliente precisa ser
 * reproduzível (BR-146).
 */
@Component
public class BalanceCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /** RN-222: percentual com 2 casas, arredondamento {@code HALF_UP} (ART-042). */
    private static final int RATE_SCALE = 2;

    /** Saldo do período a partir do estado persistido. */
    public PeriodBalance calculate(ContractPeriod period) {
        return calculate(
                period.getContractedMinutes(),
                period.getCarriedInMinutes(),
                period.getAdjustmentMinutes(),
                period.getConsumedMinutes(),
                period.getNonBillableMinutes());
    }

    /**
     * Saldo a partir de valores avulsos — usado na prévia de {@code POST /work-logs/validate} e na
     * avaliação de RN-231, onde o consumo hipotético ainda não foi persistido.
     */
    public PeriodBalance calculate(
            int contractedMinutes,
            int carriedInMinutes,
            int adjustmentMinutes,
            int consumedMinutes,
            int nonBillableMinutes) {
        // RN-218.
        int available = contractedMinutes + carriedInMinutes + adjustmentMinutes;
        // RN-220: pode ser negativo — é o excedente visto por outro ângulo.
        int remaining = available - consumedMinutes;
        // RN-221.
        int overage = Math.max(0, consumedMinutes - available);
        return new PeriodBalance(
                contractedMinutes,
                carriedInMinutes,
                adjustmentMinutes,
                available,
                consumedMinutes,
                nonBillableMinutes,
                remaining,
                overage,
                consumptionRate(available, consumedMinutes));
    }

    /**
     * RN-222: {@code available > 0 ? consumed/available × 100 : (consumed > 0 ? 100 : 0)}.
     *
     * <p>O ramo de {@code available = 0} não é defesa contra divisão por zero — é regra de negócio.
     * Um contrato {@code HOURLY_OPEN} tem {@code available = 0} por definição (RN-210, CX-03) e
     * precisa exibir {@code 0%}, não erro. E um período sem saldo que recebeu horas está 100%
     * consumido, não indefinido (CX-02).
     */
    private BigDecimal consumptionRate(int availableMinutes, int consumedMinutes) {
        if (availableMinutes > 0) {
            return BigDecimal.valueOf(consumedMinutes)
                    .multiply(ONE_HUNDRED)
                    .divide(BigDecimal.valueOf(availableMinutes), RATE_SCALE, RoundingMode.HALF_UP);
        }
        return consumedMinutes > 0
                ? ONE_HUNDRED.setScale(RATE_SCALE, RoundingMode.UNNECESSARY)
                : BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.UNNECESSARY);
    }
}
