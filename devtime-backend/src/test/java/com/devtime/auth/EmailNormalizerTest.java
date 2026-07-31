package com.devtime.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Normalização de e-mail (TS-001-02). */
class EmailNormalizerTest {

    private final EmailNormalizer normalizer = new EmailNormalizer();

    @ParameterizedTest(name = "RN-452: \"{0}\" normaliza para \"{1}\"")
    @CsvSource({
        "'  Rafael@Exemplo.COM  ', rafael@exemplo.com",
        "a@b.com, a@b.com",
        "A@B.COM, a@b.com"
    })
    @DisplayName("RN-452: o e-mail é aparado e convertido para minúsculas antes de qualquer uso")
    void shouldNormalize(String raw, String expected) {
        assertThat(normalizer.normalize(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("RN-452: a parte local não sofre nenhuma alteração além da caixa")
    void localPartMustKeepDotsAndPlusTags() {
        assertThat(normalizer.normalize("Rafael.Mendes+devtime@Exemplo.com"))
                .as("remover pontos ou sufixos +tag presumiria as regras de um provedor específico")
                .isEqualTo("rafael.mendes+devtime@exemplo.com");
    }

    @Test
    @DisplayName("AU-03: a conversão independe do locale padrão da JVM")
    void normalizationMustNotDependOnDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            // Em tr-TR, "I".toLowerCase() produz "ı" — um endereço que jamais casaria com o índice
            // uq_users_email.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(normalizer.normalize("RAFAELI@EXEMPLO.COM"))
                    .isEqualTo("rafaeli@exemplo.com");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("Entrada nula permanece nula, para que a validação de formato reporte o campo")
    void nullMustRemainNull() {
        assertThat(normalizer.normalize(null)).isNull();
    }
}
