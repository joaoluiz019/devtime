package com.devtime.dashboard;

import com.devtime.dashboard.domain.ContractSeverity;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Severidade do cartão de contrato a partir do consumo (§6.2 de specs/010).
 *
 * <p>BR-066: cálculo puro, sem efeito colateral e sem acesso a dados.
 *
 * <p><b>Os limiares são os do contrato</b> ({@code notificationThresholds}), nunca 50/80/100 fixos
 * (CP-04, OB-03). Um contrato configurado com {@code [70, 90]} passa a {@code INFO} em 70% e a
 * {@code WARNING} em 90% — os mesmos números que disparam o alerta por e-mail de RN-602. Fixá-los
 * aqui produziria dois números para a mesma situação, e o usuário não saberia em qual confiar.
 */
@Component
public class SeverityCalculator {

    /** Limiares aplicados quando o contrato não define os seus (entities.md §6.6). */
    static final List<Integer> DEFAULT_THRESHOLDS = List.of(50, 80, 100);

    private static final BigDecimal OVERAGE_RATE = BigDecimal.valueOf(100);

    /**
     * Severidade correspondente à taxa de consumo.
     *
     * @param consumptionRate percentual já apurado por {@code BalanceService}; nunca recalculado
     *     aqui (INV-DSH-01)
     * @param contractThresholds limiares do contrato, em ordem crescente; vazio aplica o padrão
     */
    public ContractSeverity calculate(
            BigDecimal consumptionRate, List<Integer> contractThresholds) {
        BigDecimal rate = consumptionRate == null ? BigDecimal.ZERO : consumptionRate;

        // RN-222 / CX-06: consumo com saldo zerado produz rate = 100 e é crítico. A verificação de
        // excedente vem primeiro porque 100% é sempre CRITICAL, mesmo que o contrato tenha
        // configurado um limiar acima disso.
        if (rate.compareTo(OVERAGE_RATE) >= 0) {
            return ContractSeverity.CRITICAL;
        }

        List<Integer> thresholds = normalize(contractThresholds);
        // CX-05 / CE-10: HOURLY_OPEN tem available = 0, logo rate = 0, logo OK — e nenhum alerta.
        // Nada a tratar aqui: a escala já produz OK abaixo do primeiro limiar.
        if (thresholds.isEmpty() || rate.compareTo(BigDecimal.valueOf(thresholds.get(0))) < 0) {
            return ContractSeverity.OK;
        }
        if (thresholds.size() == 1 || rate.compareTo(BigDecimal.valueOf(thresholds.get(1))) < 0) {
            return ContractSeverity.INFO;
        }
        return ContractSeverity.WARNING;
    }

    /**
     * Maior limiar do contrato já atingido pela taxa, base do {@code type} do alerta.
     *
     * <p>Devolve o limiar e não a severidade porque RN-603 identifica o alerta de consumo pelo
     * <b>limiar</b>: é ele que aparece em {@code CONTRACT_USAGE_80} e no {@code dedupeKey} da
     * notificação correspondente. Emitir a severidade faria a tela e o e-mail nomearem o mesmo
     * evento de formas diferentes.
     *
     * @return o limiar atingido, ou vazio quando nenhum foi
     */
    public java.util.Optional<Integer> highestReachedThreshold(
            BigDecimal consumptionRate, List<Integer> contractThresholds) {
        BigDecimal rate = consumptionRate == null ? BigDecimal.ZERO : consumptionRate;
        return normalize(contractThresholds).stream()
                .filter(threshold -> rate.compareTo(BigDecimal.valueOf(threshold)) >= 0)
                .max(Integer::compareTo);
    }

    private List<Integer> normalize(List<Integer> contractThresholds) {
        if (contractThresholds == null || contractThresholds.isEmpty()) {
            return DEFAULT_THRESHOLDS;
        }
        return contractThresholds.stream().filter(java.util.Objects::nonNull).sorted().toList();
    }
}
