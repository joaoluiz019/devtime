package com.devtime.user.dto;

/**
 * Situação da conta, exposta na fronteira da feature {@code 002-users}.
 *
 * <p>Espelha {@code UserStatus} propositalmente em vez de reexportá-lo: AR-02 proíbe que outra
 * feature dependa de {@code user.domain}, e {@code auth} precisa decidir entre "verifique seu
 * e-mail" ({@code DEVTIME-1008}) e "conta bloqueada" ({@code DEVTIME-1006}) — duas respostas
 * distintas que um booleano não distinguiria.
 */
public enum AccountStatus {

    /** Cadastrada, e-mail ainda não verificado. Não autentica (CP-08). */
    PENDING_ACTIVATION,

    /** Operacional. */
    ACTIVE,

    /** Desativada por administrador. */
    DISABLED,

    /** RN-453: bloqueio temporário por falhas de login. */
    LOCKED
}
