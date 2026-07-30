package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.RolloverPolicy;
import com.devtime.shared.error.BusinessRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Coerência entre tipo e campos do contrato (INV-CTR-02 a INV-CTR-05).
 *
 * <p>A ordem das verificações é normativa (§6.1 da spec, passos 4 a 7) e cada passo é exercitado
 * isoladamente: um validador que rejeitasse pelo motivo certo na ordem errada devolveria a mensagem
 * menos útil das duas aplicáveis.
 */
class ContractTypeCoherenceValidatorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 10);

    private final ContractTypeCoherenceValidator validator = new ContractTypeCoherenceValidator();

    @Test
    @DisplayName("INV-CTR-02: contrato mensal coerente é aceito")
    void shouldAcceptCoherentMonthlyContract() {
        assertThatCode(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        2400,
                                        RolloverPolicy.NONE,
                                        null,
                                        1,
                                        START,
                                        null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @DisplayName("RN-202: monthlyMinutes fora de 1–44.640 é rejeitado com DEVTIME-2202")
    @ValueSource(ints = {0, -1, 44641, 100000})
    void shouldRejectOutOfRangeMonthlyMinutes(int monthlyMinutes) {
        assertThatThrownBy(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        monthlyMinutes,
                                        RolloverPolicy.NONE,
                                        null,
                                        1,
                                        START,
                                        null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2202");
    }

    @Test
    @DisplayName("RN-202: os limites 1 e 44.640 são aceitos")
    void shouldAcceptRangeBoundaries() {
        assertThatCode(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        1,
                                        RolloverPolicy.NONE,
                                        null,
                                        1,
                                        START,
                                        null))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        44640,
                                        RolloverPolicy.NONE,
                                        null,
                                        1,
                                        START,
                                        null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("INV-CTR-03: HOURLY_OPEN com rollover diferente de NONE é rejeitado")
    void shouldRejectHourlyOpenWithRollover() {
        assertThatThrownBy(
                        () ->
                                validator.assertCoherent(
                                        ContractType.HOURLY_OPEN,
                                        null,
                                        RolloverPolicy.FULL,
                                        null,
                                        1,
                                        START,
                                        null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2210");
    }

    @Test
    @DisplayName("RN-210: HOURLY_OPEN sem saldo nem rollover é aceito")
    void shouldAcceptCoherentHourlyOpen() {
        assertThatCode(
                        () ->
                                validator.assertCoherent(
                                        ContractType.HOURLY_OPEN,
                                        null,
                                        RolloverPolicy.NONE,
                                        null,
                                        1,
                                        START,
                                        null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @DisplayName("RN-203: billingDay fora de 1–28 é rejeitado com DEVTIME-2203")
    @ValueSource(ints = {0, 29, 31, -5})
    void shouldRejectInvalidBillingDay(int billingDay) {
        assertThatThrownBy(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        2400,
                                        RolloverPolicy.NONE,
                                        null,
                                        billingDay,
                                        START,
                                        null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2203");
    }

    @Test
    @DisplayName("RN-204/INV-CTR-05: endDate anterior a startDate é rejeitada com DEVTIME-2204")
    void shouldRejectInvertedDateRange() {
        assertThatThrownBy(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        2400,
                                        RolloverPolicy.NONE,
                                        null,
                                        1,
                                        START,
                                        START.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2204");
    }

    @Test
    @DisplayName("CX-04: endDate igual a startDate é aceita — contrato de um dia")
    void shouldAcceptSameDayRange() {
        assertThatCode(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        2400,
                                        RolloverPolicy.NONE,
                                        null,
                                        1,
                                        START,
                                        START))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("INV-CTR-04: CAPPED sem rolloverCapMinutes é rejeitado com DEVTIME-2209")
    void shouldRejectCappedWithoutCap() {
        assertThatThrownBy(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        2400,
                                        RolloverPolicy.CAPPED,
                                        null,
                                        1,
                                        START,
                                        null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2209");
    }

    @Test
    @DisplayName("INV-CTR-04: CAPPED com teto informado é aceito")
    void shouldAcceptCappedWithCap() {
        assertThatCode(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        2400,
                                        RolloverPolicy.CAPPED,
                                        300,
                                        1,
                                        START,
                                        null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("§6.1: o tipo é verificado antes do billingDay — a mensagem mais útil prevalece")
    void shouldReportTypeErrorBeforeBillingDay() {
        assertThatThrownBy(
                        () ->
                                validator.assertCoherent(
                                        ContractType.MONTHLY_HOURS,
                                        null,
                                        RolloverPolicy.NONE,
                                        null,
                                        99,
                                        START,
                                        null))
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2202");
    }
}
