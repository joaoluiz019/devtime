package com.devtime.timer.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Exceções de regra da feature 009 (spec §27).
 *
 * <p>BR-063: método fábrica nomeado pela regra.
 *
 * <p>§28 / §19.1: <b>nenhum detalhe carrega {@code description}</b> nem {@code reason} de pausa.
 */
public final class TimerExceptions {

    private TimerExceptions() {}

    /**
     * RN-150 / INV-TMR-01: já existe cronômetro ativo do usuário.
     *
     * <p>O limite é por <b>usuário entre todos os tenants</b> (CE-13, CX-01). Os detalhes informam
     * em qual organização o cronômetro existente está: sem isso, alguém que participa de dois
     * tenants receberia um erro sobre um cronômetro que não consegue ver na tela atual.
     */
    public static BusinessRuleException alreadyActive(UUID activeTimerId, UUID activeTenantId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activeTimerId", activeTimerId);
        details.put("activeTenantId", activeTenantId);
        details.put("suggestedAction", "STOP_CURRENT");
        return new TimerConflictException(
                ErrorCode.TIMER_ALREADY_ACTIVE, details, "Já existe um cronômetro ativo");
    }

    /** RN-153: pausar exige {@code RUNNING}. Auto-transição não é idempotência, é erro. */
    public static BusinessRuleException notRunning(TimerStatus current) {
        return new TimerConflictException(
                ErrorCode.TIMER_NOT_RUNNING,
                Map.of("currentStatus", current.name()),
                "O cronômetro não está em execução");
    }

    /** RN-155: retomar exige {@code PAUSED}. */
    public static BusinessRuleException notPaused(TimerStatus current) {
        return new TimerConflictException(
                ErrorCode.TIMER_NOT_PAUSED,
                Map.of("currentStatus", current.name()),
                "O cronômetro não está pausado");
    }

    /** ME-04: operação sobre cronômetro em estado terminal. */
    public static BusinessRuleException terminal(TimerStatus current) {
        return new TimerConflictException(
                ErrorCode.INVALID_STATE_TRANSITION,
                Map.of("currentStatus", current.name()),
                "Este cronômetro já foi encerrado");
    }

    /**
     * RN-165: a janela de 7 dias para recuperar um cronômetro abandonado terminou.
     *
     * <p>Os detalhes trazem o prazo original, porque a mensagem sozinha não permite ao usuário
     * saber por quanto tempo ele perdeu a oportunidade.
     */
    public static BusinessRuleException notRecoverable(LocalDate recoverableUntil) {
        return new TimerConflictException(
                ErrorCode.TIMER_NOT_RECOVERABLE,
                Map.of("recoverableUntil", recoverableUntil.toString()),
                "Cronômetro abandonado não pode mais ser recuperado");
    }

    /**
     * RN-158: o encerramento exige descrição com ao menos 3 caracteres.
     *
     * <p>O código é o mesmo de RN-105 ({@code DEVTIME-2105}) porque a exigência é a mesma — a
     * descrição do work log que será gerado. A fábrica vive aqui, e não em {@code worklog}, porque
     * {@code timer} não pode depender do domínio de outra feature (AR-02): a regra é compartilhada,
     * o pacote não.
     *
     * <p>Verificada <b>antes</b> de qualquer alteração de estado: é o caminho de erro mais
     * frequente do encerramento, e RN-160 exige que o cronômetro fique intocado nele.
     */
    public static BusinessRuleException descriptionRequired(int length) {
        return new TimerValidationException(
                ErrorCode.WORKLOG_DESCRIPTION_INVALID,
                Map.of("field", "description", "length", length, "min", 3),
                "Descrição obrigatória (mínimo 3 caracteres)");
    }

    /** RN-104: nenhuma categoria ativa disponível para o cronômetro. */
    public static BusinessRuleException categoryInvalid() {
        return new TimerValidationException(
                ErrorCode.CATEGORY_INVALID_OR_INACTIVE,
                Map.of("field", "categoryId"),
                "Categoria inválida ou inativa");
    }

    /** RN-162: o descarte destrói trabalho registrado e exige confirmação explícita. */
    public static BusinessRuleException discardNotConfirmed() {
        return new TimerValidationException(
                ErrorCode.VALIDATION_FAILED,
                Map.of("field", "confirm"),
                "O descarte do cronômetro exige confirmação explícita");
    }

    /** RN-153, RN-155, RN-150, RN-165 e ME-04 — todos {@code 409}. */
    public static final class TimerConflictException extends BusinessRuleException {
        private TimerConflictException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }

    /** RN-162. */
    public static final class TimerValidationException extends BusinessRuleException {
        private TimerValidationException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }
}
