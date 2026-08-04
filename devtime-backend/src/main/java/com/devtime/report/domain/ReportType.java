package com.devtime.report.domain;

/**
 * Os cinco tipos de relatório (§4 de reports.md, §3 de specs/012).
 *
 * <p>Fechado por design (RS-08): não existe relatório personalizado com colunas configuráveis. Cada
 * tipo tem um recorte, um conjunto de agrupamentos compatíveis e uma permissão — e é a combinação
 * dos três que {@code ReportGroupingPolicy} e {@code ReportScopePolicy} verificam.
 */
public enum ReportType {

    /** §6: o relatório mais importante do produto. Único que pode vir de snapshot (RN-701). */
    CONTRACT_PERIOD,

    /** §7.1: consolida todos os contratos de um cliente no intervalo. Exige REPORT_VIEW_ANY. */
    CLIENT_SUMMARY,

    /** §7.2: folha de horas por intervalo livre, independente de contrato. */
    TIMESHEET,

    /** §7.3: histórico completo de um ticket. */
    TICKET_DETAIL,

    /**
     * §7.4: métricas agregadas. Exige REPORT_VIEW_ANY.
     *
     * <p>IDG-02 de {@code personas.md}: <b>nunca</b> compara membros entre si nem produz ranking. O
     * agrupamento por usuário mostra valores absolutos, sem classificação.
     */
    PRODUCTIVITY
}
