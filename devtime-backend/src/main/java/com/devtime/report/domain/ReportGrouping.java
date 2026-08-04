package com.devtime.report.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Critério de agrupamento das linhas do detalhamento (§5.1 de reports.md, §6.3 de specs/012).
 *
 * <p><b>Divergência resolvida.</b> §6.3 de {@code specs/012} tabela seis agrupamentos e §5.1 de
 * {@code reports.md} tabela sete — o sétimo é {@link #NONE}, a lista plana. Vale {@code reports.md}
 * pela hierarquia IA-11, e a diferença não é acadêmica: sem {@code NONE} não existe forma de pedir
 * o detalhamento sem cabeçalhos de grupo, que é exatamente o que o CSV de §9.3 é.
 *
 * <p>O agrupamento é configurável; a <b>ordenação dentro dele não é</b> (CP-05). Duas gerações do
 * mesmo relatório listam as linhas na mesma sequência — data, {@code ticketKey}, {@code startedAt}
 * —, e é isso que torna RN-708 verificável.
 */
public enum ReportGrouping {

    /** Default de §5.1. Aplicável aos cinco tipos. */
    DATE,

    /** Semana ISO. Apenas folha de horas e produtividade (§6.3). */
    WEEK,

    TICKET,

    CATEGORY,

    USER,

    TAG,

    /** Lista plana, sem cabeçalhos de grupo. */
    NONE;

    /**
     * Agrupamentos aceitos por tipo de relatório (§6.3).
     *
     * <p>{@code TICKET_DETAIL} não aceita {@code TICKET}: o relatório <b>é</b> de um único ticket,
     * e agrupar por ele produziria um grupo só, com o mesmo rótulo do cabeçalho. Também não aceita
     * {@code USER} nem {@code TAG} porque §6.3 os restringe a período, cliente e folha de horas.
     */
    public static Set<ReportGrouping> supportedBy(ReportType reportType) {
        return switch (reportType) {
            case CONTRACT_PERIOD, CLIENT_SUMMARY ->
                    EnumSet.of(DATE, TICKET, CATEGORY, USER, TAG, NONE);
            case TIMESHEET -> EnumSet.allOf(ReportGrouping.class);
            case TICKET_DETAIL -> EnumSet.of(DATE, CATEGORY, NONE);
            case PRODUCTIVITY -> EnumSet.of(DATE, WEEK, CATEGORY, USER, NONE);
        };
    }
}
