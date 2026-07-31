package com.devtime.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Geração de slug (TS-001-03, INV-TEN-01, CX-03). */
class SlugGeneratorTest {

    /** Regex normativo de entities.md §6.1. */
    private static final Pattern SLUG_PATTERN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{0,58}[a-z0-9])?$");

    private final SlugGenerator generator = new SlugGenerator();
    private final Predicate<String> nothingTaken = slug -> false;

    @Test
    @DisplayName("INV-TEN-01: o nome da organização vira slug em minúsculas com hífens")
    void shouldDeriveSlugFromName() {
        assertThat(generator.generate("Rafael Mendes Dev", nothingTaken))
                .isEqualTo("rafael-mendes-dev")
                .matches(SLUG_PATTERN);
    }

    @Test
    @DisplayName("INV-TEN-01: acentos são reduzidos e símbolos viram separador")
    void shouldStripAccentsAndSymbols() {
        assertThat(generator.generate("Açaí & Cia", nothingTaken))
                .as("o & não pode virar 'e' nem desaparecer colando as palavras")
                .isEqualTo("acai-cia")
                .matches(SLUG_PATTERN);
    }

    @Test
    @DisplayName("INV-TEN-01: nome sem caractere aproveitável gera fallback determinístico")
    void shouldFallbackWhenNameHasNoUsableCharacter() {
        assertThat(generator.generate("---", nothingTaken)).isEqualTo("org").matches(SLUG_PATTERN);
    }

    @Test
    @DisplayName("INV-TEN-01: o slug é truncado em 60 caracteres, sem terminar em hífen")
    void shouldTruncateToSixtyCharacters() {
        String slug = generator.generate("Organização ".repeat(20), nothingTaken);

        assertThat(slug).hasSizeLessThanOrEqualTo(60).matches(SLUG_PATTERN);
    }

    @Test
    @DisplayName("CX-03: colisão de slug resolve com sufixo numérico incremental")
    void shouldResolveCollisionWithNumericSuffix() {
        Set<String> taken = Set.of("acme-software", "acme-software-2");

        assertThat(generator.generate("Acme Software", taken::contains))
                .isEqualTo("acme-software-3")
                .matches(SLUG_PATTERN);
    }

    @Test
    @DisplayName("CX-03: o cadastro nunca falha por causa do slug, mesmo com muitos homônimos")
    void shouldNeverFailBecauseOfSlug() {
        String slug = generator.generate("Acme Software", slug1 -> !slug1.contains("-"));

        assertThat(slug)
                .as("esgotadas as tentativas sequenciais, o sufixo aleatório encerra a busca")
                .matches(SLUG_PATTERN);
    }

    @Test
    @DisplayName("INV-TEN-01: o sufixo de colisão respeita o limite de 60 caracteres")
    void suffixMustRespectMaximumLength() {
        String longName = "a".repeat(60);

        assertThat(generator.generate(longName, slug -> slug.length() == 60))
                .hasSizeLessThanOrEqualTo(60)
                .matches(SLUG_PATTERN);
    }
}
