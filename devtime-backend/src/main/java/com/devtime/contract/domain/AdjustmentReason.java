package com.devtime.contract.domain;

/**
 * Motivo do ajuste de saldo (entities.md §6.8).
 *
 * <p>O motivo é obrigatório e não substitui a justificativa (RN-215): o enum permite agrupar
 * ajustes em relatório, o texto explica <b>este</b> ajuste. Um sem o outro é insuficiente — a
 * categoria sozinha não defende a decisão, e o texto sozinho não é agregável.
 */
public enum AdjustmentReason {
    /** Horas concedidas como cortesia comercial. */
    COURTESY,
    /** Correção de lançamento ou de apuração. */
    CORRECTION,
    /** Horas extras negociadas fora do contrato mensal. */
    NEGOTIATED_EXTRA,
    /** Débito acordado. */
    PENALTY,
    /** Saldo trazido de sistema anterior. */
    MIGRATION,
    /** RN-230: usado também pela expiração automática de saldo transportado. */
    OTHER
}
