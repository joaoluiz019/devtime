package com.devtime.dashboard.domain;

/** Períodos oferecidos pelo seletor do painel (§10.1 de reports.md, §17.1 de specs/010). */
public enum DashboardPeriodType {

    /** Padrão: o período de apuração corrente, resolvido no fuso do tenant. */
    CURRENT_PERIOD,
    LAST_7_DAYS,
    LAST_30_DAYS,

    /** Exige {@code from} e {@code to}; limitado a 366 dias (RN-705). */
    CUSTOM
}
