package com.devtime.dashboard;

import com.devtime.dashboard.dto.DashboardResponses.ChartSliceDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Ajusta o resto de arredondamento dos percentuais (CP-06 de specs/010).
 *
 * <p>Três fatias de um terço cada arredondadas a 2 casas somam 99,99. A diferença vai para a
 * <b>maior</b> fatia, onde é proporcionalmente menor e passa despercebida — atribuí-la à menor
 * produziria o "Outros: −0,01%" que a regra existe para evitar.
 *
 * <p>{@code BigDecimal} e não {@code double}: o percentual é exibido ao usuário com 2 casas, e o
 * ponto flutuante binário produziria {@code 48.319999999} onde a tela espera {@code 48,32}.
 */
@Component
public class PercentageNormalizer {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    /**
     * Atribui o percentual de cada fatia sobre o total, com o resto na maior.
     *
     * @param slices fatias já rotuladas, com {@code percentage} ainda não definido
     * @return novas fatias com o percentual calculado; lista vazia produz lista vazia
     */
    public List<ChartSliceDto> normalize(List<ChartSliceDto> slices) {
        int total = slices.stream().mapToInt(ChartSliceDto::minutes).sum();
        if (slices.isEmpty() || total == 0) {
            // CX-02 de specs/010: intervalo sem registros produz gráfico vazio com mensagem, não
            // fatias com percentual indefinido.
            return slices.stream()
                    .map(slice -> withPercentage(slice, BigDecimal.ZERO.setScale(SCALE)))
                    .toList();
        }

        BigDecimal totalMinutes = BigDecimal.valueOf(total);
        List<ChartSliceDto> normalized = new ArrayList<>(slices.size());
        for (ChartSliceDto slice : slices) {
            BigDecimal percentage =
                    BigDecimal.valueOf(slice.minutes())
                            .multiply(HUNDRED)
                            .divide(totalMinutes, SCALE, RoundingMode.HALF_UP); // ART-042
            normalized.add(withPercentage(slice, percentage));
        }

        BigDecimal sum =
                normalized.stream()
                        .map(ChartSliceDto::percentage)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainder = HUNDRED.setScale(SCALE).subtract(sum);
        if (remainder.signum() == 0) {
            return List.copyOf(normalized);
        }

        int largest =
                java.util.stream.IntStream.range(0, normalized.size())
                        .boxed()
                        .max(Comparator.comparingInt(index -> normalized.get(index).minutes()))
                        .orElse(0);
        normalized.set(
                largest,
                withPercentage(
                        normalized.get(largest),
                        normalized.get(largest).percentage().add(remainder)));
        return List.copyOf(normalized);
    }

    private ChartSliceDto withPercentage(ChartSliceDto slice, BigDecimal percentage) {
        return new ChartSliceDto(
                slice.entityId(), slice.label(), slice.color(), slice.minutes(), percentage);
    }
}
