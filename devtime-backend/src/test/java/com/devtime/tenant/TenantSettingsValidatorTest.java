package com.devtime.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.tenant.dto.TenantSettings;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T-002-40: bordas de faixa e validação cruzada das 10 chaves (users.md §6.2).
 *
 * <p>Cada faixa aqui protege um cálculo. Um teste por borda, e não por chave: o erro que importa
 * não é "aceitou um valor absurdo", é "aceitou o valor imediatamente fora do permitido".
 */
class TenantSettingsValidatorTest {

    private final TenantSettingsValidator validator = new TenantSettingsValidator();

    private TenantSettings settingsWith(
            int workDayMinutes, int longRunning, int autoAbandon, int rounding) {
        return new TenantSettings(
                workDayMinutes,
                List.of(1, 2, 3, 4, 5),
                "NONE",
                "WARN",
                longRunning,
                autoAbandon,
                false,
                30,
                rounding,
                List.of(50, 80, 100));
    }

    @Test
    @DisplayName("Os padrões de entities.md §6.1.1 são válidos")
    void defaultsAreValid() {
        assertThatCode(() -> validator.validate(TenantSettings.defaults()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("workDayMinutes aceita as bordas 60 e 1440 e rejeita 59 e 1441")
    void workDayMinutesRange() {
        assertThatCode(() -> validator.validate(settingsWith(60, 480, 960, 0)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(settingsWith(1440, 480, 960, 0)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(settingsWith(59, 480, 960, 0)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> validator.validate(settingsWith(1441, 480, 960, 0)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("RN-164 / CX-09: timerAutoAbandonMinutes menor ou igual ao de alerta é rejeitado")
    void timerThresholdsMustBeOrdered() {
        assertThatThrownBy(() -> validator.validate(settingsWith(480, 600, 600, 0)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.TIMER_THRESHOLDS_INCONSISTENT));
        assertThatThrownBy(() -> validator.validate(settingsWith(480, 600, 599, 0)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> validator.validate(settingsWith(480, 600, 601, 0)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 6, 10, 15, 30})
    @DisplayName("RN-113: apenas os arredondamentos suportados são aceitos")
    void roundingAccepted(int minutes) {
        assertThatCode(() -> validator.validate(settingsWith(480, 480, 960, minutes)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 20, 60})
    @DisplayName("RN-113: arredondamento fora do conjunto devolve DEVTIME-2021")
    void roundingRejected(int minutes) {
        assertThatThrownBy(() -> validator.validate(settingsWith(480, 480, 960, minutes)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ROUNDING_MINUTES_UNSUPPORTED));
    }

    @Test
    @DisplayName("workDays exige subconjunto não vazio de 1 a 7, sem repetição")
    void workDaysMustBeValidSubset() {
        assertThatThrownBy(() -> validator.validate(withWorkDays(List.of())))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> validator.validate(withWorkDays(List.of(0, 1))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> validator.validate(withWorkDays(List.of(1, 8))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> validator.validate(withWorkDays(List.of(1, 1, 2))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> validator.validate(withWorkDays(List.of(6, 7))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RN-602: notificationThresholds aceita até 5 valores entre 1 e 500")
    void thresholdsRange() {
        assertThatCode(() -> validator.validate(withThresholds(List.of(1, 500))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(withThresholds(List.of())))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> validator.validate(withThresholds(List.of(1, 2, 3, 4, 5, 6))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> validator.validate(withThresholds(List.of(501))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("CX-10: limiares desordenados ou duplicados são normalizados, não rejeitados")
    void thresholdsAreNormalized() {
        assertThat(validator.normalizeThresholds(List.of(80, 50, 80, 100)))
                .containsExactly(50, 80, 100);
    }

    @Test
    @DisplayName("As políticas padrão aceitam apenas os valores de entities.md §6.1.1")
    void policiesAreClosedSets() {
        assertThatThrownBy(() -> validator.validate(withRollover("PARCIAL")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> validator.validate(withRollover("CAPPED"))).doesNotThrowAnyException();
    }

    private TenantSettings withWorkDays(List<Integer> workDays) {
        TenantSettings base = TenantSettings.defaults();
        return new TenantSettings(
                base.workDayMinutes(),
                workDays,
                base.defaultRolloverPolicy(),
                base.defaultOveragePolicy(),
                base.timerLongRunningMinutes(),
                base.timerAutoAbandonMinutes(),
                base.allowFutureWorkLogs(),
                base.retroactiveLimitDays(),
                base.roundingMinutes(),
                base.notificationThresholds());
    }

    private TenantSettings withThresholds(List<Integer> thresholds) {
        TenantSettings base = TenantSettings.defaults();
        return new TenantSettings(
                base.workDayMinutes(),
                base.workDays(),
                base.defaultRolloverPolicy(),
                base.defaultOveragePolicy(),
                base.timerLongRunningMinutes(),
                base.timerAutoAbandonMinutes(),
                base.allowFutureWorkLogs(),
                base.retroactiveLimitDays(),
                base.roundingMinutes(),
                thresholds);
    }

    private TenantSettings withRollover(String policy) {
        TenantSettings base = TenantSettings.defaults();
        return new TenantSettings(
                base.workDayMinutes(),
                base.workDays(),
                policy,
                base.defaultOveragePolicy(),
                base.timerLongRunningMinutes(),
                base.timerAutoAbandonMinutes(),
                base.allowFutureWorkLogs(),
                base.retroactiveLimitDays(),
                base.roundingMinutes(),
                base.notificationThresholds());
    }
}
