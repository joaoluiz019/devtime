package com.devtime.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T-012-34 — neutralização de fórmula em célula de planilha (SG-05, CE-17, CP-17).
 *
 * <p>O ataque atravessa o sistema sem tocá-lo: a descrição é gravada como texto inofensivo e vira
 * execução de código na máquina de um <b>terceiro</b> — o cliente que abre o arquivo exportado e
 * nunca teve conta no DevTime.
 */
class FormulaInjectionSanitizerTest {

    private final FormulaInjectionSanitizer sanitizer = new FormulaInjectionSanitizer();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "=SUM(A1:A9)",
                "+1+1",
                "-2+3",
                "@SUM(A1)",
                "=cmd|'/c calc'!A1",
                "=HYPERLINK(\"http://exemplo\",\"clique\")"
            })
    @DisplayName("SG-05 / CA-17: célula iniciando com =, +, - ou @ é neutralizada")
    void formulaStartersAreNeutralized(String payload) {
        assertThat(sanitizer.sanitize(payload)).isEqualTo("'" + payload);
    }

    @Test
    @DisplayName("SG-05: espaço à esquerda não protege — as planilhas o descartam antes de decidir")
    void leadingWhitespaceDoesNotBypass() {
        assertThat(sanitizer.sanitize(" =SUM(A1)")).isEqualTo("' =SUM(A1)");
        assertThat(sanitizer.sanitize("\t-1")).isEqualTo("'\t-1");
    }

    @Test
    @DisplayName("O conteúdo do usuário é preservado, nunca removido")
    void contentIsPreservedNotStripped() {
        // Uma descrição legítima começando com hífen — "-2h de retrabalho" — perderia sentido se o
        // caractere fosse removido. A neutralização muda a interpretação, não o dado.
        assertThat(sanitizer.sanitize("-2h de retrabalho")).isEqualTo("'-2h de retrabalho");
    }

    @Test
    @DisplayName("Texto comum atravessa intacto; nulo permanece nulo")
    void ordinaryTextIsUntouched() {
        assertThat(sanitizer.sanitize("Implementação do cálculo de frete"))
                .isEqualTo("Implementação do cálculo de frete");
        assertThat(sanitizer.sanitize("")).isEmpty();
        assertThat(sanitizer.sanitize(null))
                .as("célula ausente e célula vazia são coisas diferentes no XLSX")
                .isNull();
    }

    @Test
    @DisplayName("CA-18 / CX-12: emoji e acentuação atravessam sem alteração")
    void emojiAndAccentsSurvive() {
        assertThat(sanitizer.sanitize("Correção 🚀 no checkout"))
                .isEqualTo("Correção 🚀 no checkout");
    }
}
