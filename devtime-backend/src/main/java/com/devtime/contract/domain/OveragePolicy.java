package com.devtime.contract.domain;

/**
 * Tratamento do excedente de horas (RN-231 a RN-233).
 *
 * <p>Aplicada por {@code 008-worklogs} no registro de horas; ignorada em contratos {@code
 * HOURLY_OPEN} (RN-210).
 */
public enum OveragePolicy {
    /** Rejeita o registro que ultrapassaria o saldo disponível. */
    BLOCK,
    /** Permite e avisa. Padrão. */
    WARN,
    /** Permite e marca os minutos excedentes para cobrança à {@code overageRate}. */
    ALLOW_BILLABLE
}
