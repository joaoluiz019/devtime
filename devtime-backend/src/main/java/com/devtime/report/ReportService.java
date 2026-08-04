package com.devtime.report;

import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.report.dto.ReportResponses.ClientSummaryReportResponse;
import com.devtime.report.dto.ReportResponses.ContractPeriodReportResponse;
import com.devtime.report.dto.ReportResponses.ProductivityReportResponse;
import com.devtime.report.dto.ReportResponses.TicketDetailReportResponse;
import com.devtime.report.dto.ReportResponses.TimesheetReportResponse;
import java.util.UUID;

/**
 * Os cinco relatórios (§22.2 de specs/012, §6 e §7 de reports.md).
 *
 * <p><b>Esta feature não publica interface para outras features</b> (§22.2). Como {@code
 * 010-dashboard}, é folha no grafo — mas, diferentemente dela, é {@code P0}: o produto sem
 * relatório não tem entregável. Esta interface existe para o controller e para {@code
 * ExportService}, ambos dentro da própria feature.
 *
 * <p>Todos os métodos aplicam a ordem normativa da §6.2, e a ordem <b>é</b> o contrato: ela
 * determina qual erro o usuário vê quando mais de uma regra é violada. Em particular, o escopo
 * (passo 2) precede a existência do recurso (passo 3), para que o código de erro não revele que um
 * contrato inacessível existe.
 */
public interface ReportService {

    /** §6 — o relatório mais importante do produto. Snapshot ou ao vivo, conforme §6.1. */
    ContractPeriodReportResponse contractPeriod(UUID periodId, ReportFilters filters);

    /** §7.1 — todos os contratos de um cliente no intervalo. Exige {@code REPORT_VIEW_ANY}. */
    ClientSummaryReportResponse clientSummary(UUID clientId, ReportFilters filters);

    /** §7.2 — folha de horas por intervalo livre, independente de contrato. */
    TimesheetReportResponse timesheet(ReportFilters filters);

    /** §7.3 — histórico completo de um ticket. */
    TicketDetailReportResponse ticketDetail(UUID ticketId, ReportFilters filters);

    /** §7.4 — métricas agregadas. Exige {@code REPORT_VIEW_ANY}; nunca produz ranking (IDG-02). */
    ProductivityReportResponse productivity(ReportFilters filters);
}
