package com.devtime.tag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tabela normativa de normalização (RN-506, §6.1 de specs/006-tags).
 *
 * <p>Suíte escrita <b>antes</b> do normalizador (T-006-03): a tabela da spec é o oráculo. Escrita
 * depois, ela confirmaria o que o código faz — inclusive seus erros, que seriam silenciosos e
 * permanentes no dado.
 */
class TagNormalizerTest {

    private final TagNormalizer normalizer = new TagNormalizer();

    @ParameterizedTest(name = "\"{0}\" normaliza para \"{1}\"")
    @CsvSource(
            delimiter = '|',
            value = {
                "Code Review|code-review",
                "  urgente  |urgente",
                "REFATORAÇÃO|refatoração",
                "migracao   v2|migracao-v2",
                "débito-técnico|débito-técnico",
                "a|a",
                "ab|ab",
                "--|--",
                "code-review|code-review"
            })
    @DisplayName("RN-506: reproduz a tabela normativa da §6.1 da spec 006")
    void shouldReproduceNormativeTable(String input, String expected) {
        assertThat(normalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("RN-506/CX-02: acentos são preservados — refatoração e refatoracao coexistem")
    void shouldPreserveAccents() {
        assertThat(normalizer.normalize("Refatoração")).isEqualTo("refatoração");
        assertThat(normalizer.normalize("Refatoracao")).isEqualTo("refatoracao");
        assertThat(normalizer.normalize("Refatoração"))
                .as("removê-los tornaria a etiqueta ilegível a quem a digitou")
                .isNotEqualTo(normalizer.normalize("Refatoracao"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"v2.1", "api/rest", "c#", "🚀-lançamento"})
    @DisplayName("RN-506: caracteres especiais não são filtrados — são rótulos legítimos")
    void shouldNotFilterSpecialCharacters(String input) {
        assertThat(normalizer.normalize(input)).isEqualTo(input.toLowerCase(java.util.Locale.ROOT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Code Review", "  urgente ", "migracao   v2", "REFATORAÇÃO"})
    @DisplayName("CE-02: a normalização é idempotente — aplicá-la duas vezes não muda o resultado")
    void shouldBeIdempotent(String input) {
        String once = normalizer.normalize(input);
        assertThat(normalizer.normalize(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("CX-03: nome só com espaços normaliza para vazio, e não para um hífen")
    void shouldNormalizeBlankToEmpty() {
        assertThat(normalizer.normalize("   ")).isEmpty();
        assertThat(normalizer.normalize("")).isEmpty();
        assertThat(normalizer.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("RN-506: bug e bugs permanecem distintos — não há stemming nem singularização")
    void shouldNotApplyStemming() {
        assertThat(normalizer.normalize("bug")).isNotEqualTo(normalizer.normalize("bugs"));
    }

    @Test
    @DisplayName("CX-05: entrada longa com muitos espaços encolhe e passa a caber no limite")
    void shouldShrinkWhitespaceHeavyInput() {
        String raw = "Sprint     2026     Q1     Plano";
        assertThat(normalizer.normalize(raw)).isEqualTo("sprint-2026-q1-plano");
        assertThat(normalizer.normalize(raw).length()).isLessThan(raw.length());
    }
}
