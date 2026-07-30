package com.devtime.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.client.domain.DocumentType;
import com.devtime.shared.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Validação de CPF e CNPJ (RN-402, T-003-23).
 *
 * <p>As tabelas de válidos e inválidos são fixas (BR-206: nenhum teste usa dado aleatório) e
 * incluem os dois modos de falha que importam: dígito verificador errado e sequência de dígitos
 * repetidos, que passa na fórmula mas nunca corresponde a um documento real (CX-04).
 */
class DocumentValidatorTest {

    private final DocumentValidator validator = new DocumentValidator();
    private final DocumentNormalizer normalizer = new DocumentNormalizer();

    @ParameterizedTest
    @DisplayName("RN-402: CPFs com dígitos verificadores corretos são aceitos")
    @ValueSource(
            strings = {
                "52998224725",
                "11144477735",
                "12345678909",
                "39053344705",
                "16899535009",
                "04740294095",
                "68952088018",
                "22233366638"
            })
    void shouldAcceptValidCpf(String document) {
        assertThat(validator.isValidCpf(document)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("RN-402: CPFs com dígito verificador incorreto são rejeitados")
    @ValueSource(
            strings = {
                "52998224726",
                "11144477736",
                "12345678900",
                "39053344700",
                "1234567890",
                "123456789012",
                "abcdefghijk"
            })
    void shouldRejectInvalidCpf(String document) {
        assertThat(validator.isValidCpf(document)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("CX-04: CPF com todos os dígitos iguais é rejeitado, embora passe na fórmula")
    @ValueSource(
            strings = {
                "00000000000",
                "11111111111",
                "22222222222",
                "33333333333",
                "44444444444",
                "55555555555",
                "66666666666",
                "77777777777",
                "88888888888",
                "99999999999"
            })
    void shouldRejectRepeatedDigitCpf(String document) {
        assertThat(validator.isValidCpf(document)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("RN-402: CNPJs com dígitos verificadores corretos são aceitos")
    @ValueSource(
            strings = {
                "11222333000181",
                "04252011000110",
                "34028316000103",
                "33000167000101",
                "60746948000112",
                "47960950000121"
            })
    void shouldAcceptValidCnpj(String document) {
        assertThat(validator.isValidCnpj(document)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("RN-402: CNPJs inválidos são rejeitados, inclusive sequências repetidas")
    @ValueSource(
            strings = {
                "11222333000182",
                "04252011000111",
                "00000000000000",
                "11111111111111",
                "1122233300018",
                "112223330001812"
            })
    void shouldRejectInvalidCnpj(String document) {
        assertThat(validator.isValidCnpj(document)).isFalse();
    }

    @Test
    @DisplayName("CX-05: CNPJ de filial com a mesma raiz da matriz é válido")
    void shouldAcceptBranchCnpjWithSameRoot() {
        assertThat(validator.isValidCnpj("33000167000101")).isTrue();
        assertThat(validator.isValidCnpj("33000167000292")).isTrue();
    }

    @Test
    @DisplayName("CX-03: a máscara é removida antes da validação")
    void shouldValidateMaskedDocument() {
        String normalized = normalizer.normalize("11.222.333/0001-81");

        assertThat(normalized).isEqualTo("11222333000181");
        assertThat(validator.isValidCnpj(normalized)).isTrue();
    }

    @Test
    @DisplayName("CE-C-01: cliente sem documento não é validado")
    void shouldSkipValidationWithoutDocument() {
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("   ")).isNull();
        validator.assertValid(DocumentType.CPF, null); // não lança
    }

    @Test
    @DisplayName("RN-402: documento OTHER não passa por dígito verificador")
    void shouldNotValidateOtherDocumentType() {
        validator.assertValid(DocumentType.OTHER, "XYZ123"); // não lança
    }

    @Test
    @DisplayName("DEVTIME-2402: documento inválido lança exceção com o campo identificado")
    void shouldThrowWithErrorCode() {
        assertThatThrownBy(() -> validator.assertValid(DocumentType.CPF, "11111111111"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2402");
    }
}
