package com.devtime.worklog.domain;

/**
 * Origem do registro de horas (entities.md §6.13).
 *
 * <p>🔒 RN-126: imutável após a criação. A métrica "% de horas capturadas por cronômetro" é uma
 * medida de qualidade do produto; permitir converter um registro manual em registro de timer a
 * corromperia silenciosamente.
 *
 * <p>{@code IMPORT} e {@code AI_SUGGESTION} existem no enum sem caminho de entrada no MVP
 * (importação em massa é F5; sugestão automática é {@code future/020-ai}). Ambas reusarão {@code
 * WorkLogService.create} integralmente, pelo mesmo motivo de RN-159.
 */
public enum WorkLogSource {
    MANUAL,
    TIMER,
    IMPORT,
    AI_SUGGESTION
}
