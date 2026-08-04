package com.devtime.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.dashboard.dto.DashboardResponses.ChartSliceDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Percentuais que somam 100 (CP-06, CA-12, CX-14 de specs/010). */
class PercentageNormalizerTest {

    private final PercentageNormalizer normalizer = new PercentageNormalizer();

    private static ChartSliceDto slice(String label, int minutes) {
        return new ChartSliceDto(UUID.randomUUID(), label, "#000000", minutes, null);
    }

    private static BigDecimal sumOf(List<ChartSliceDto> slices) {
        return slices.stream()
                .map(ChartSliceDto::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("CX-14 / CA-12: três terços somam 100, com o resto na maior fatia")
    void remainderGoesToLargestSlice() {
        List<ChartSliceDto> normalized =
                normalizer.normalize(List.of(slice("A", 100), slice("B", 100), slice("C", 100)));

        assertThat(sumOf(normalized)).isEqualByComparingTo(new BigDecimal("100.00"));
        // Empate em minutos: o resto vai para a primeira das maiores, e o total continua exato.
        assertThat(normalized).extracting(ChartSliceDto::percentage).hasSize(3);
    }

    @Test
    @DisplayName("CP-06: o resto nunca cai na menor fatia, onde produziria distorção visível")
    void largestSliceAbsorbsRemainder() {
        List<ChartSliceDto> normalized =
                normalizer.normalize(List.of(slice("grande", 1000), slice("pequena", 1)));

        assertThat(sumOf(normalized)).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(normalized.get(0).percentage())
                .as("a maior recebe o ajuste")
                .isGreaterThan(normalized.get(1).percentage());
        assertThat(normalized.get(1).percentage()).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    @DisplayName("ART-042: o percentual tem 2 casas, com arredondamento HALF_UP")
    void twoDecimalPlaces() {
        List<ChartSliceDto> normalized =
                normalizer.normalize(List.of(slice("A", 4320), slice("B", 4620)));

        assertThat(normalized.get(0).percentage().scale()).isEqualTo(2);
        assertThat(sumOf(normalized)).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("CX-02: intervalo sem registros produz fatias zeradas, não divisão por zero")
    void zeroTotalDoesNotDivide() {
        List<ChartSliceDto> normalized =
                normalizer.normalize(List.of(slice("A", 0), slice("B", 0)));

        assertThat(normalized)
                .allSatisfy(
                        item ->
                                assertThat(item.percentage())
                                        .isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("FA-02: nenhuma fatia produz lista vazia, não exceção")
    void emptyInput() {
        assertThat(normalizer.normalize(List.of())).isEmpty();
    }

    @Test
    @DisplayName("CP-06: uma única fatia recebe exatamente 100%")
    void singleSlice() {
        assertThat(normalizer.normalize(List.of(slice("única", 777))).get(0).percentage())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("CP-06: rótulo, cor e minutos são preservados; só o percentual é atribuído")
    void preservesEverythingElse() {
        ChartSliceDto original = slice("Acme", 120);
        ChartSliceDto normalized = normalizer.normalize(List.of(original)).get(0);

        assertThat(normalized.entityId()).isEqualTo(original.entityId());
        assertThat(normalized.label()).isEqualTo("Acme");
        assertThat(normalized.color()).isEqualTo("#000000");
        assertThat(normalized.minutes()).isEqualTo(120);
    }
}
