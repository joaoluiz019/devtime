package com.devtime.contract.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exceções do banco de horas — feature 011 (spec §27).
 *
 * <p>BR-063: método fábrica nomeado pela regra.
 *
 * <p>§19.1: a {@code justification} de um ajuste ou de uma reabertura <b>nunca</b> aparece em
 * detalhe de erro nem em log — é texto livre com possível conteúdo comercial sensível.
 */
public final class BalanceExceptions {

    private BalanceExceptions() {}

    /** RN-235: ajuste exige período {@code OPEN} ou {@code REOPENED}. */
    public static BusinessRuleException periodNotAdjustable(PeriodStatus status) {
        return new BalanceConflictException(
                ErrorCode.PERIOD_NOT_ADJUSTABLE,
                Map.of("currentStatus", status.name(), "suggestedAction", "REOPEN_PERIOD"),
                "Ajuste só é permitido em período aberto");
    }

    /**
     * RN-237: o ajuste deixaria {@code availableMinutes} negativo.
     *
     * <p>CX-08: deixar exatamente zero é permitido — a regra proíbe negativo, não zero. Os detalhes
     * informam quanto ainda pode ser debitado, para que a correção seja possível sem tentativa e
     * erro.
     */
    public static BusinessRuleException wouldMakeBalanceNegative(
            int availableMinutes, int requestedMinutes) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("availableMinutes", availableMinutes);
        details.put("requestedMinutes", requestedMinutes);
        details.put("maxDebitMinutes", -availableMinutes);
        return new BalanceValidationException(
                ErrorCode.ADJUSTMENT_WOULD_MAKE_BALANCE_NEGATIVE,
                details,
                "O ajuste deixaria o saldo disponível negativo");
    }

    /** RN-215: justificativa com no mínimo 10 caracteres. */
    public static BusinessRuleException justificationTooShort(int length) {
        return new BalanceValidationException(
                ErrorCode.JUSTIFICATION_REQUIRED,
                Map.of("field", "justification", "length", length, "min", 10),
                "Justificativa obrigatória (mínimo 10 caracteres)");
    }

    /** RN-239: fechamento antes do {@code endDate} exige confirmação explícita. */
    public static BusinessRuleException closeTooEarly(LocalDate endDate) {
        return new BalanceConflictException(
                ErrorCode.PERIOD_CLOSE_TOO_EARLY,
                Map.of("endDate", endDate.toString(), "requiresConfirmation", true),
                "Período ainda não pode ser fechado");
    }

    /**
     * RN-240: existe cronômetro ativo cujo trabalho pertenceria ao período.
     *
     * <p>A lista viaja nos detalhes porque a ação corretiva é encerrar aqueles cronômetros — e sem
     * saber quais são, quem fecha o período não consegue fazê-lo.
     */
    public static BusinessRuleException periodHasActiveTimer(List<UUID> timerIds) {
        return new BalanceConflictException(
                ErrorCode.PERIOD_HAS_ACTIVE_TIMER,
                Map.of("activeTimerIds", timerIds),
                "Existe cronômetro ativo no período");
    }

    /**
     * RN-244: existe período posterior já fechado.
     *
     * <p>O {@code carriedIn} do posterior derivou do {@code carriedOut} deste; reabrir este
     * invalidaria um período já congelado. A reabertura vai do mais recente para o mais antigo, e o
     * detalhe indica qual reabrir primeiro (CX-16).
     */
    public static BusinessRuleException laterPeriodClosed(UUID laterPeriodId, int laterSequence) {
        return new BalanceConflictException(
                ErrorCode.PERIOD_LATER_ALREADY_CLOSED,
                Map.of(
                        "laterPeriodId", laterPeriodId,
                        "laterSequence", laterSequence,
                        "suggestedAction", "REOPEN_LATER_FIRST"),
                "Existe período posterior já fechado");
    }

    /** ME-04: transição fora da matriz §4.6. */
    public static BusinessRuleException invalidPeriodTransition(PeriodStatus from, String action) {
        return new BalanceConflictException(
                ErrorCode.INVALID_STATE_TRANSITION,
                Map.of("currentStatus", from.name(), "action", action),
                "Operação não permitida neste estado do período");
    }

    /** RN-235, RN-239, RN-240, RN-244 e ME-04 — todos {@code 409}. */
    public static final class BalanceConflictException extends BusinessRuleException {
        private BalanceConflictException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }

    /** RN-215 e RN-237 — {@code 422}. */
    public static final class BalanceValidationException extends BusinessRuleException {
        private BalanceValidationException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }
}
