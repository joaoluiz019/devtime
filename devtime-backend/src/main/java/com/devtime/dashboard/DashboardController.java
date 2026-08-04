package com.devtime.dashboard;

import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.dashboard.dto.DashboardResponses.ChartResponse;
import com.devtime.dashboard.dto.DashboardResponses.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Painel operacional (§10 de reports.md, §22.1 de specs/010).
 *
 * <p>Os dois endpoints são de leitura pura: a feature não cria, atualiza nem exclui nada (RS-01), e
 * por isso não gera {@code AuditLog} (CP-13) — cada abertura do painel poluiria a trilha com
 * dezenas de entradas sem valor investigativo. O acesso é registrado em log de aplicação (§28).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Visão operacional do tenant e gráficos de distribuição")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardChartService chartService;

    @GetMapping
    @Operation(
            summary = "Painel completo",
            description =
                    "Estatísticas rápidas, cartões de contrato ordenados por criticidade, alertas do"
                            + " estado atual, registros recentes, tickets abertos e três gráficos."
                            + " O escopo (`TENANT` ou `USER`) é derivado do papel, nunca recebido."
                            + " Blocos que falharem são listados em `failedBlocks` e os demais são"
                            + " devolvidos normalmente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Painel composto"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "`DEVTIME-3001` intervalo acima de 366 dias; `DEVTIME-2000` intervalo"
                                + " personalizado incompleto ou invertido"),
        @ApiResponse(responseCode = "403", description = "`DEVTIME-1101` sem permissão de painel")
    })
    public DashboardResponse dashboard(
            @RequestParam(required = false) DashboardPeriodType period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return dashboardService.load(period, from, to);
    }

    @GetMapping("/chart/{type}")
    @Operation(
            summary = "Gráfico isolado",
            description =
                    "Recarrega um único gráfico ao trocar o período, sem recarregar o painel"
                            + " inteiro (FA-07). Tipos: `daily-minutes`, `by-client`,"
                            + " `by-category`, `by-contract`, `billable-ratio`,"
                            + " `consumption-trend`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pontos ou fatias, conforme o tipo"),
        @ApiResponse(responseCode = "400", description = "`DEVTIME-2000` tipo de gráfico inválido"),
        @ApiResponse(responseCode = "403", description = "`DEVTIME-1101` sem permissão de painel")
    })
    public ChartResponse chart(
            @PathVariable String type,
            @RequestParam(required = false) DashboardPeriodType period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return chartService.chart(type, period, from, to);
    }
}
