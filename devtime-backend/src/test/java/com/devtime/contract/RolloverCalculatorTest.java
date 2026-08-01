package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.contract.domain.RolloverPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tabela normativa de carry-over (RN-224 a RN-228, §6.2 de specs/011).
 *
 * <p>Os seis casos são reproduzidos na ordem em que a tabela os apresenta. O quinto — saldo
 * negativo com política {@code FULL} — é o que sustenta RN-228: transportar dívida transformaria um
 * problema pontual em permanente e tornaria o saldo incompreensível para o cliente.
 */
class RolloverCalculatorTest {

    private final RolloverCalculator calculator = new RolloverCalculator();

    @ParameterizedTest(name = "[{index}] {0} cap={1} restante={2} ⇒ transportado={3} ({4})")
    @CsvSource({
        "NONE,     , 600,   0, 'saldo positivo é perdido'",
        "FULL,     , 600, 600, 'tudo transportado'",
        "CAPPED, 300, 600, 300, 'limitado ao teto'",
        "CAPPED, 300, 150, 150, 'abaixo do teto'",
        "FULL,     ,-500,   0, 'negativo não transporta (RN-228)'",
        "NONE,     ,   0,   0, 'consumo exato'"
    })
    @DisplayName("RN-225 a RN-228: a tabela normativa de carry-over é reproduzida nos 6 casos")
    void normativeRolloverTable(
            RolloverPolicy policy, Integer cap, int remaining, int expected, String reason) {
        assertThat(calculator.carriedOut(policy, remaining, cap)).as(reason).isEqualTo(expected);
    }

    @Test
    @DisplayName("RN-228: nenhuma política transporta saldo negativo")
    void noPolicyEverCarriesNegativeBalance() {
        for (RolloverPolicy policy : RolloverPolicy.values()) {
            assertThat(calculator.carriedOut(policy, -1, 1000))
                    .as("%s com restante negativo", policy)
                    .isZero();
        }
    }

    @Test
    @DisplayName("CX-05: CAPPED com teto zero equivale a NONE — aceito e documentado")
    void cappedWithZeroCapBehavesAsNone() {
        assertThat(calculator.carriedOut(RolloverPolicy.CAPPED, 600, 0)).isZero();
    }

    @Test
    @DisplayName("CX-04: restante exatamente zero não transporta em nenhuma política")
    void exactConsumptionCarriesNothing() {
        for (RolloverPolicy policy : RolloverPolicy.values()) {
            assertThat(calculator.carriedOut(policy, 0, 300)).isZero();
        }
    }

    @Test
    @DisplayName("RN-227: teto nulo em CAPPED é tratado como zero, nunca como ilimitado")
    void cappedWithNullCapDoesNotBecomeUnlimited() {
        // INV-CTR-04 exige rolloverCapMinutes em CAPPED; se ele chegar nulo por dado legado,
        // transportar tudo seria a falha mais cara — o cliente receberia horas que ninguém
        // concedeu.
        assertThat(calculator.carriedOut(RolloverPolicy.CAPPED, 600, null)).isZero();
    }
}
