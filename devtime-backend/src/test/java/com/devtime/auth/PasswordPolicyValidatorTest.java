package com.devtime.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Política de senha (TS-001-01).
 *
 * <p>A tabela de entradas reproduz literalmente TS-001-01 do plano de testes, incluindo os casos
 * que existem para provar decisões não óbvias: espaços não contam como complexidade, e acentos são
 * permitidos.
 */
class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @ParameterizedTest(name = "RN-451: aceita \"{0}\"")
    // "A1b2c3d4e5" ficou de fora de propósito: consta na lista de senhas comuns, o que a torna um
    // caso de rejeição, não de aceitação — exatamente o que PW-03 pretende.
    @ValueSource(strings = {"SenhaForte123", "Sênhã Fôrte123", "Relatorio7Horas"})
    @DisplayName("RN-451: senha conforme a política é aceita")
    void shouldAcceptCompliantPassword(String password) {
        assertThatCode(() -> validator.validate(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "RN-451: rejeita \"{0}\" por {1}")
    @CsvSource({
        "senhaforte123, UPPERCASE",
        "SENHAFORTE123, LOWERCASE",
        "SenhaForteAbc, DIGIT",
        "Senha12, MIN_LENGTH",
        "Password123, COMMON_PASSWORD"
    })
    @DisplayName("RN-451: senha fora da política é rejeitada com DEVTIME-2451")
    void shouldRejectNonCompliantPassword(String password, String expectedViolation) {
        assertThatThrownBy(() -> validator.validate(password))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        thrown -> {
                            BusinessRuleException failure = (BusinessRuleException) thrown;
                            assertThat(failure.getErrorCode())
                                    .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);
                            assertThat(failure.getDetails().get("requirements").toString())
                                    .contains(expectedViolation);
                        });
    }

    @Test
    @DisplayName("RN-451: espaços não contam como complexidade — \"Ab1\" + 7 espaços é rejeitada")
    void whitespaceMustNotCountTowardsMinimumLength() {
        assertThatThrownBy(() -> validator.validate("Ab1       "))
                .as("dez caracteres, mas apenas três significativos")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("RN-451: senha acima de 128 caracteres é rejeitada")
    void shouldRejectPasswordAboveMaximumLength() {
        String tooLong = "Aa1" + "x".repeat(126);

        assertThatThrownBy(() -> validator.validate(tooLong))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("PW-08: a exceção nunca ecoa a senha informada")
    void failureMustNotEchoThePassword() {
        String secret = "senhaerrada";

        assertThatThrownBy(() -> validator.validate(secret))
                .satisfies(
                        thrown -> {
                            assertThat(thrown.getMessage()).doesNotContain(secret);
                            assertThat(((BusinessRuleException) thrown).getDetails().toString())
                                    .doesNotContain(secret);
                        });
    }

    @Test
    @DisplayName("RN-451: senha nula é rejeitada como ausência de todos os requisitos")
    void nullPasswordMustBeRejected() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
