package com.devtime.contract.domain;

/**
 * Política de transporte de saldo entre períodos (RN-225 a RN-227).
 *
 * <p>O cálculo de {@code carriedOutMinutes} pertence a {@code 011-bank-hours}, que ocorre no
 * fechamento. Aqui a política é apenas configurada e validada (INV-CTR-04).
 */
public enum RolloverPolicy {
    /** Saldo positivo é perdido no fechamento. */
    NONE,
    FULL,
    /** Transporta até {@code rolloverCapMinutes}. */
    CAPPED
}
