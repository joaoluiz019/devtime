package com.devtime.contract.domain;

/**
 * Situação do contrato (state-machines.md §4.5).
 *
 * <p>{@code ENDED} e {@code CANCELLED} são terminais: reativar recriaria a sequência de períodos
 * com lacuna temporal, quebrando INV-PER-03. O caminho correto é criar um novo contrato (CE-15).
 */
public enum ContractStatus {
    /** Em elaboração. Não gera período nem aceita registro de horas. */
    DRAFT,
    ACTIVE,
    /** Parado temporariamente: aceita apenas registro retroativo dentro da vigência (RN-306). */
    SUSPENDED,
    ENDED,
    CANCELLED;

    public boolean isTerminal() {
        return this == ENDED || this == CANCELLED;
    }
}
