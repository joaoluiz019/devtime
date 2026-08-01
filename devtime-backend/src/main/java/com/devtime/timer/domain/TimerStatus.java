package com.devtime.timer.domain;

/**
 * Situação do cronômetro (state-machines.md §4.8).
 *
 * <p>{@code COMPLETED} e {@code DISCARDED} são terminais. Não existe transição de saída deles: o
 * work log já existe e é ele a entidade a editar, e o descarte é irreversível por definição
 * (RN-162).
 */
public enum TimerStatus {
    /** Contando tempo ativo. */
    RUNNING,
    /** Congelado, com uma {@code TimerPause} aberta (INV-TMR-02). */
    PAUSED,
    /** RN-164: ultrapassou o limiar sem ação. Recuperável por 7 dias (RN-165). */
    ABANDONED,
    /** Encerrado com sucesso; {@code workLogId} preenchido (INV-TMR-04). */
    COMPLETED,
    /** RN-162: descartado explicitamente; nenhum work log gerado (INV-TMR-05). */
    DISCARDED;

    /** RN-150 / INV-TMR-01: os dois estados que contam para o limite de um por usuário. */
    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == DISCARDED;
    }
}
