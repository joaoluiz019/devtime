package com.devtime.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Máscara de dados sensíveis em log (ART-084, security.md §9.2).
 *
 * <p>CA-03 de security.md: senha, token e dados sensíveis não aparecem em nenhum log, verificado
 * por teste. É este o teste.
 */
class SensitiveDataMaskerTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"password\":\"SenhaSuperSecreta1\"}",
                "password=SenhaSuperSecreta1",
                "{\"senha\": \"SenhaSuperSecreta1\"}",
                "{\"passwordHash\":\"$2a$12$abcdefghijklmnopqrstuv\"}",
                "jwtSecret=SenhaSuperSecreta1"
            })
    @DisplayName("A senha nunca é registrada, em nenhum formato")
    void mustMaskPasswords(String message) {
        assertThat(SensitiveDataMasker.mask(message))
                .doesNotContain("SenhaSuperSecreta1")
                .doesNotContain("$2a$12$abcdefghijklmnopqrstuv")
                .contains("***");
    }

    @Test
    @DisplayName("O token do header Authorization é mascarado, preservando o esquema")
    void mustMaskBearerToken() {
        String message =
                "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhYmMifQ.c2lnbmF0dXJl";

        String masked = SensitiveDataMasker.mask(message);

        assertThat(masked).contains("Bearer ***").doesNotContain("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    @DisplayName("Um JWT solto na mensagem é mascarado")
    void mustMaskLooseJwt() {
        String message = "token recusado: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhYmMifQ.c2lnbmF0dXJl";

        assertThat(SensitiveDataMasker.mask(message))
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                .contains("***");
    }

    @Test
    @DisplayName(
            "security.md §9.2: o e-mail preserva o domínio e apenas 2 caracteres da parte local")
    void mustMaskEmailPreservingDomain() {
        String masked = SensitiveDataMasker.mask("login falhou para rafael.silva@exemplo.com");

        assertThat(masked).isEqualTo("login falhou para ra****@exemplo.com");
    }

    @Test
    @DisplayName("security.md §9.2: CPF preserva apenas os 3 últimos dígitos")
    void mustMaskCpfKeepingLastThreeDigits() {
        assertThat(SensitiveDataMasker.mask("documento 529.982.247-25"))
                .isEqualTo("documento ***725");
    }

    @Test
    @DisplayName("security.md §9.2: CNPJ preserva apenas os 3 últimos dígitos")
    void mustMaskCnpjKeepingLastThreeDigits() {
        assertThat(SensitiveDataMasker.mask("documento 11.222.333/0001-81"))
                .isEqualTo("documento ***181");
    }

    @Test
    @DisplayName("Sequência numérica que não é CPF nem CNPJ é preservada")
    void mustNotMaskUnrelatedNumbers() {
        assertThat(SensitiveDataMasker.mask("duracao 480 minutos no periodo 2026-07"))
                .isEqualTo("duracao 480 minutos no periodo 2026-07");
    }

    @Test
    @DisplayName("Mensagem sem dado sensível permanece intacta")
    void mustPreserveHarmlessMessage() {
        String message = "Requisição rejeitada. code=DEVTIME-2002 status=404 path=/api/v1/clients";

        assertThat(SensitiveDataMasker.mask(message)).isEqualTo(message);
    }

    @Test
    @DisplayName("Entrada nula ou vazia não quebra a máscara")
    void mustHandleNullAndEmpty() {
        assertThat(SensitiveDataMasker.mask(null)).isNull();
        assertThat(SensitiveDataMasker.mask("")).isEmpty();
    }
}
