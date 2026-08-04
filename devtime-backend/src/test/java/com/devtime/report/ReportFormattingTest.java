package com.devtime.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-012-33 — formatação de duração e de valor (RN-709, RN-710, CP-06, CP-07).
 *
 * <p>Os dois formatadores são puros e determinísticos (BR-150), e é isso que os torna testáveis à
 * mão: cada asserção aqui é uma conta que o leitor do relatório consegue conferir.
 */
class ReportFormattingTest {

    private final DurationFormatter durationFormatter = new DurationFormatter();
    private final MoneyFormatter moneyFormatter = new MoneyFormatter();

    @Test
    @DisplayName("RN-710 / ART-035: duração em HH:MM, sem teto de 24 horas")
    void durationLabelHasNoHourCeiling() {
        assertThat(durationFormatter.toLabel(150)).isEqualTo("02:30");
        assertThat(durationFormatter.toLabel(0)).isEqualTo("00:00");
        // 51:35 é o total do exemplo normativo de §6 — um relatório mensal passa de 24h por
        // construção, e um formato que virasse "03:35" mentiria sobre 48 horas de trabalho.
        assertThat(durationFormatter.toLabel(3095)).isEqualTo("51:35");
        assertThat(durationFormatter.toLabel(8940)).isEqualTo("149:00");
    }

    @Test
    @DisplayName("Duração negativa leva o sinal antes das horas, nunca dentro dos minutos")
    void negativeDurationKeepsSignOutside() {
        assertThat(durationFormatter.toLabel(-150)).isEqualTo("-02:30");
    }

    @Test
    @DisplayName("RN-710 / XLS-02: horas decimais com 2 casas — a coluna somável do XLSX")
    void decimalHoursAreSummable() {
        assertThat(durationFormatter.toDecimalHours(150)).isEqualByComparingTo("2.50");
        assertThat(durationFormatter.toDecimalHours(3095)).isEqualByComparingTo("51.58");
        // 1 minuto não é zero: arredondar para baixo aqui apagaria o registro da planilha.
        assertThat(durationFormatter.toDecimalHours(1)).isEqualByComparingTo("0.02");
    }

    @Test
    @DisplayName("RN-709 / CP-07: valor monetário usa HALF_UP, nunca truncamento")
    void moneyUsesHalfUp() {
        BigDecimal rate = new BigDecimal("150.0000");

        // 46 horas a 150,00 — o exemplo de §6.
        assertThat(moneyFormatter.valueOf(2760, rate)).isEqualByComparingTo("6900.00");
        // 1 minuto a 150,00/hora = 2,50 exatos.
        assertThat(moneyFormatter.valueOf(1, rate)).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("RN-709: a divisão por 60 não arredonda antes da escala final")
    void intermediateDivisionKeepsPrecision() {
        // 7 minutos a 100,00/hora = 11,666...; com arredondamento intermediário a 2 casas o
        // resultado sairia de uma conta já degradada, e o erro cresceria com a quantidade de
        // linhas.
        assertThat(moneyFormatter.valueOf(7, new BigDecimal("100.0000")))
                .isEqualByComparingTo("11.67");
    }

    @Test
    @DisplayName("CE-R-05 / FA-18: contrato sem valor hora devolve nulo, nunca zero")
    void missingRateOmitsValue() {
        assertThat(moneyFormatter.valueOf(2760, null))
                .as("zero afirmaria que o trabalho não vale nada; nulo diz que não há como afirmar")
                .isNull();
    }
}
