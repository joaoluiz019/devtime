package com.devtime.report;

import com.devtime.report.domain.ReportGrouping;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.report.dto.ReportResponses.ClientSummaryReportResponse;
import com.devtime.report.dto.ReportResponses.ContractPeriodReportResponse;
import com.devtime.report.dto.ReportResponses.ProductivityReportResponse;
import com.devtime.report.dto.ReportResponses.TicketDetailReportResponse;
import com.devtime.report.dto.ReportResponses.TimesheetReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Os cinco relatórios (§6 e §7 de reports.md).
 *
 * <p>BR-080: nenhuma regra aqui. Escopo, intervalo, agrupamento e resolução de fonte são
 * verificados no <b>serviço</b> — verificar apenas na fronteira HTTP deixaria o caminho da
 * exportação, que recompõe os mesmos relatórios sem passar por este controller, sem proteção
 * alguma.
 *
 * <p>ART-021 / BR-090: nenhum endpoint aceita {@code tenantId} nem identificador de solicitante. O
 * escopo é derivado do papel.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Relatórios", description = "O entregável do produto: horas em forma de documento")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/contract-period/{periodId}")
    @Operation(
            summary = "Relatório de período de contrato",
            description =
                    "Período `CLOSED` é servido **exclusivamente** do snapshot e marcado como"
                            + " definitivo (RN-701); `OPEN` e `REOPENED` são calculados ao vivo e"
                            + " marcados como **PARCIAL** (RN-702). `MEMBER` recebe apenas os"
                            + " próprios registros.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relatório composto"),
        @ApiResponse(responseCode = "403", description = "`DEVTIME-1101` escopo não permitido"),
        @ApiResponse(responseCode = "404", description = "`DEVTIME-2002` período de outro tenant"),
        @ApiResponse(
                responseCode = "409",
                description = "`DEVTIME-3002` período ainda não iniciado"),
        @ApiResponse(
                responseCode = "422",
                description = "`DEVTIME-3007` agrupamento não suportado pelo tipo")
    })
    public ContractPeriodReportResponse contractPeriod(
            @PathVariable UUID periodId,
            @RequestParam(required = false) ReportGrouping groupBy,
            @RequestParam(required = false) Boolean includeNonBillable,
            @RequestParam(required = false) Boolean includeFinancial,
            @RequestParam(required = false) Boolean includeUserColumn,
            @RequestParam(required = false) List<UUID> categoryIds,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) List<UUID> userIds) {
        return reportService.contractPeriod(
                periodId,
                new ReportFilters(
                        groupBy,
                        includeNonBillable,
                        includeFinancial,
                        includeUserColumn,
                        null,
                        null,
                        null,
                        null,
                        categoryIds,
                        tagIds,
                        userIds,
                        null));
    }

    @GetMapping("/client-summary/{clientId}")
    @Operation(
            summary = "Resumo consolidado por cliente",
            description =
                    "Uma seção por contrato, com o saldo do próprio contrato, e totais **separados"
                            + " por moeda** — não há conversão (CE-R-09). Exige"
                            + " `REPORT_VIEW_ANY`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumo composto"),
        @ApiResponse(
                responseCode = "400",
                description = "`DEVTIME-3001` intervalo acima de 366 dias"),
        @ApiResponse(responseCode = "403", description = "`DEVTIME-1101` exige REPORT_VIEW_ANY"),
        @ApiResponse(responseCode = "404", description = "`DEVTIME-2002` cliente de outro tenant")
    })
    public ClientSummaryReportResponse clientSummary(
            @PathVariable UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ReportGrouping groupBy,
            @RequestParam(required = false) List<UUID> contractIds,
            @RequestParam(required = false) List<UUID> categoryIds,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) List<UUID> userIds,
            @RequestParam(required = false) Boolean billable) {
        return reportService.clientSummary(
                clientId,
                new ReportFilters(
                        groupBy,
                        null,
                        null,
                        null,
                        from,
                        to,
                        contractIds,
                        null,
                        categoryIds,
                        tagIds,
                        userIds,
                        billable));
    }

    @GetMapping("/timesheet")
    @Operation(
            summary = "Folha de horas por intervalo livre",
            description =
                    "Independente de contrato, até 366 dias (RN-705). Sempre marcado como"
                            + " **PARCIAL**: o intervalo pode conter período aberto (CX-23).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Folha composta"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "`DEVTIME-3001` acima de 366 dias; `DEVTIME-2000` intervalo incompleto ou"
                                + " invertido"),
        @ApiResponse(responseCode = "403", description = "`DEVTIME-1101` escopo não permitido")
    })
    public TimesheetReportResponse timesheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ReportGrouping groupBy,
            @RequestParam(required = false) List<UUID> contractIds,
            @RequestParam(required = false) List<UUID> clientIds,
            @RequestParam(required = false) List<UUID> categoryIds,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) List<UUID> userIds,
            @RequestParam(required = false) Boolean billable) {
        return reportService.timesheet(
                new ReportFilters(
                        groupBy,
                        null,
                        null,
                        null,
                        from,
                        to,
                        contractIds,
                        clientIds,
                        categoryIds,
                        tagIds,
                        userIds,
                        billable));
    }

    @GetMapping("/ticket-detail/{ticketId}")
    @Operation(
            summary = "Detalhamento por ticket",
            description = "Estimativa contra realizado e todos os registros do ticket.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalhamento composto"),
        @ApiResponse(responseCode = "403", description = "`DEVTIME-1101` escopo não permitido"),
        @ApiResponse(responseCode = "404", description = "`DEVTIME-2002` ticket de outro tenant")
    })
    public TicketDetailReportResponse ticketDetail(
            @PathVariable UUID ticketId,
            @RequestParam(required = false) ReportGrouping groupBy,
            @RequestParam(required = false) Boolean includeNonBillable,
            @RequestParam(required = false) Boolean includeFinancial,
            @RequestParam(required = false) List<UUID> categoryIds,
            @RequestParam(required = false) List<UUID> userIds) {
        return reportService.ticketDetail(
                ticketId,
                new ReportFilters(
                        groupBy,
                        includeNonBillable,
                        includeFinancial,
                        null,
                        null,
                        null,
                        null,
                        null,
                        categoryIds,
                        null,
                        userIds,
                        null));
    }

    @GetMapping("/productivity")
    @Operation(
            summary = "Relatório de produtividade",
            description =
                    "Métricas agregadas por usuário e por semana ISO. **Nunca** compara membros"
                            + " entre si nem produz ranking (IDG-02): as linhas saem em ordem"
                            + " alfabética. Exige `REPORT_VIEW_ANY`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Métricas compostas"),
        @ApiResponse(
                responseCode = "400",
                description = "`DEVTIME-3001` intervalo acima de 366 dias"),
        @ApiResponse(responseCode = "403", description = "`DEVTIME-1101` exige REPORT_VIEW_ANY")
    })
    public ProductivityReportResponse productivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ReportGrouping groupBy,
            @RequestParam(required = false) List<UUID> contractIds,
            @RequestParam(required = false) List<UUID> categoryIds,
            @RequestParam(required = false) List<UUID> userIds,
            @RequestParam(required = false) Boolean billable) {
        return reportService.productivity(
                new ReportFilters(
                        groupBy,
                        null,
                        null,
                        null,
                        from,
                        to,
                        contractIds,
                        null,
                        categoryIds,
                        null,
                        userIds,
                        billable));
    }
}
