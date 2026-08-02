package com.devtime.tenant;

import com.devtime.tenant.domain.TenantExceptions;
import com.devtime.tenant.dto.TenantSettings;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Faixas e validação cruzada das 10 chaves operacionais (users.md §6.2, spec 002 §22.3).
 *
 * <p>Valida o <b>valor efetivo</b>, resultado da mescla entre o que veio na requisição e o que já
 * estava persistido. É o que torna a validação cruzada correta em atualização parcial: enviar
 * apenas {@code timerLongRunningMinutes = 1200} sobre um {@code timerAutoAbandonMinutes = 960}
 * inverteria os limiares sem que a requisição, isolada, revelasse o problema.
 *
 * <p>Cada faixa aqui protege um cálculo (R-02): {@code roundingMinutes} decide quanto o cliente é
 * cobrado (RN-113) e {@code retroactiveLimitDays} decide quem lança horas de meses atrás (RN-120).
 * Um valor fora da faixa não quebra a requisição que o gravou — quebra todos os registros
 * seguintes.
 */
@Component
public class TenantSettingsValidator {

    /** RN-113: conjunto fechado de users.md §6.2. */
    static final List<Integer> ALLOWED_ROUNDING = List.of(0, 5, 6, 10, 15, 30);

    /**
     * Valores de {@code entities.md} §6.1.1, declarados como texto.
     *
     * <p>Deliberadamente <b>não</b> derivados de {@code RolloverPolicy}/{@code OveragePolicy}:
     * AR-02 proíbe que {@code tenant} alcance o domínio de {@code contract}. As duas chaves são
     * apenas pré-preenchimento de formulário de contrato, e a coerência entre as listas é
     * verificada por teste em vez de por acoplamento de compilação.
     */
    static final List<String> ROLLOVER_POLICIES = List.of("NONE", "FULL", "CAPPED");

    static final List<String> OVERAGE_POLICIES = List.of("BLOCK", "WARN", "ALLOW_BILLABLE");

    static final int MIN_WORK_DAY_MINUTES = 60;
    static final int MAX_WORK_DAY_MINUTES = 1440;
    static final int MIN_TIMER_MINUTES = 60;
    static final int MAX_TIMER_MINUTES = 1440;
    static final int MAX_RETROACTIVE_DAYS = 365;
    static final int MAX_THRESHOLDS = 5;
    static final int MIN_THRESHOLD = 1;
    static final int MAX_THRESHOLD = 500;

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2000}, {@code
     *     DEVTIME-2020} ou {@code DEVTIME-2021} conforme a chave rejeitada
     */
    public void validate(TenantSettings settings) {
        assertRange(
                "workDayMinutes",
                settings.workDayMinutes(),
                MIN_WORK_DAY_MINUTES,
                MAX_WORK_DAY_MINUTES);
        assertWorkDays(settings.workDays());
        assertEnum("defaultRolloverPolicy", settings.defaultRolloverPolicy(), ROLLOVER_POLICIES);
        assertEnum("defaultOveragePolicy", settings.defaultOveragePolicy(), OVERAGE_POLICIES);
        assertRange(
                "timerLongRunningMinutes",
                settings.timerLongRunningMinutes(),
                MIN_TIMER_MINUTES,
                MAX_TIMER_MINUTES);
        assertRange(
                "timerAutoAbandonMinutes",
                settings.timerAutoAbandonMinutes(),
                MIN_TIMER_MINUTES,
                MAX_TIMER_MINUTES);
        // RN-164: o abandono é sempre posterior ao alerta.
        if (settings.timerAutoAbandonMinutes() <= settings.timerLongRunningMinutes()) {
            throw TenantExceptions.timerThresholdsInconsistent(
                    settings.timerLongRunningMinutes(), settings.timerAutoAbandonMinutes());
        }
        assertRange(
                "retroactiveLimitDays", settings.retroactiveLimitDays(), 0, MAX_RETROACTIVE_DAYS);
        if (!ALLOWED_ROUNDING.contains(settings.roundingMinutes())) {
            throw TenantExceptions.roundingNotSupported(
                    settings.roundingMinutes(), ALLOWED_ROUNDING);
        }
        assertThresholds(settings.notificationThresholds());
    }

    /**
     * CX-10: ordena e remove duplicatas antes de persistir.
     *
     * <p>Normalizar em vez de rejeitar porque a ordem não carrega intenção — {@code [80, 50]} e
     * {@code [50, 80]} pedem exatamente os mesmos dois alertas. O excesso de valores, esse sim, é
     * rejeitado: cinco limiares já produzem cinco notificações por período.
     */
    public List<Integer> normalizeThresholds(List<Integer> thresholds) {
        if (thresholds == null) {
            return null;
        }
        return thresholds.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
    }

    private void assertThresholds(List<Integer> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            throw TenantExceptions.settingOutOfRange(
                    "notificationThresholds", thresholds, "1 a 5 valores");
        }
        if (thresholds.size() > MAX_THRESHOLDS) {
            throw TenantExceptions.settingOutOfRange(
                    "notificationThresholds", thresholds.size(), "no máximo 5 valores");
        }
        thresholds.forEach(
                threshold ->
                        assertRange(
                                "notificationThresholds", threshold, MIN_THRESHOLD, MAX_THRESHOLD));
    }

    private void assertWorkDays(List<Integer> workDays) {
        if (workDays == null || workDays.isEmpty()) {
            throw TenantExceptions.settingOutOfRange("workDays", workDays, "subconjunto de 1 a 7");
        }
        Set<Integer> distinct = Set.copyOf(workDays);
        boolean valid =
                distinct.size() == workDays.size()
                        && workDays.stream().allMatch(day -> day >= 1 && day <= 7);
        if (!valid) {
            throw TenantExceptions.settingOutOfRange(
                    "workDays", workDays, "subconjunto de 1 a 7 sem repetição");
        }
    }

    private void assertRange(String key, int value, int min, int max) {
        if (value < min || value > max) {
            throw TenantExceptions.settingOutOfRange(key, value, min + " a " + max);
        }
    }

    private void assertEnum(String key, String value, List<String> allowed) {
        if (value == null || !allowed.contains(value)) {
            throw TenantExceptions.settingOutOfRange(key, value, String.join(", ", allowed));
        }
    }
}
