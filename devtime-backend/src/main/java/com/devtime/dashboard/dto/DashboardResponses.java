package com.devtime.dashboard.dto;

import com.devtime.dashboard.domain.ContractSeverity;
import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.dashboard.domain.DashboardScope;
import com.devtime.dashboard.domain.ProjectionStatus;
import com.devtime.ticket.dto.TicketResponses.TicketDashboardItem;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de saída do painel (§23 de specs/010, §10 de reports.md).
 *
 * <p><b>Nenhum campo monetário.</b> Não é omissão condicional: {@code ContractStatusDto} de §23 e o
 * exemplo normativo de reports.md §10.1 não expõem valor algum, o que satisfaz INV-DSH-04 por
 * construção. Um campo que existisse e fosse anulado por permissão continuaria aparecendo no
 * contrato da API e convidaria a inferências pela sua ausência.
 */
public final class DashboardResponses {

    private DashboardResponses() {}

    /** Intervalo efetivamente aplicado, devolvido para que a tela exiba o que foi consultado. */
    @Schema(name = "DashboardPeriodDto")
    public record DashboardPeriodDto(DashboardPeriodType type, LocalDate from, LocalDate to) {}

    /**
     * Estatísticas rápidas (§10.1).
     *
     * @param activeTimerMinutes minutos decorridos do cronômetro ativo do usuário; {@code 0} quando
     *     não há nenhum. CX-11: é sempre do tenant corrente
     */
    @Schema(name = "QuickStatsDto")
    public record QuickStatsDto(
            int todayMinutes,
            String todayLabel,
            int weekMinutes,
            String weekLabel,
            int periodMinutes,
            String periodLabel,
            int activeTimerMinutes) {}

    /**
     * Cartão de contrato.
     *
     * @param consumptionRate percentual com 2 casas, vindo de {@code BalanceService} — nunca
     *     recalculado aqui (INV-DSH-01)
     * @param daysRemaining dias até o fim do período, no fuso do tenant; zero no último dia
     * @param isPartial RN-702: período aberto produz números em evolução
     */
    @Schema(name = "ContractStatusDto")
    public record ContractStatusDto(
            UUID contractId,
            String code,
            String name,
            String clientName,
            String clientColor,
            UUID periodId,
            String periodLabel,
            int availableMinutes,
            int consumedMinutes,
            int remainingMinutes,
            BigDecimal consumptionRate,
            ContractSeverity severity,
            int daysRemaining,
            int projectedConsumedMinutes,
            ProjectionStatus projectionStatus,
            boolean isPartial) {}

    /**
     * Alerta derivado do <b>estado atual</b> (CP-03).
     *
     * @param type identificador estável do alerta. Os de consumo carregam o limiar atingido ({@code
     *     CONTRACT_USAGE_80}), montado a partir de {@code contract.notificationThresholds} — um
     *     contrato com {@code [70, 90]} produz {@code CONTRACT_USAGE_70}, pelo mesmo motivo que
     *     {@code NotificationType} recusa fixar os limiares como valores de enum (RN-603)
     */
    @Schema(name = "DashboardAlertDto")
    public record DashboardAlertDto(
            String type,
            ContractSeverity severity,
            String message,
            String entityType,
            UUID entityId) {}

    /** Ponto da série diária. Sempre 30 deles, com zeros explícitos (CP-04, INV-DSH-03). */
    @Schema(name = "ChartPointDto")
    public record ChartPointDto(LocalDate date, int netMinutes, int billableMinutes) {}

    /** Fatia de um gráfico de distribuição. {@code percentage} com 2 casas, somando 100 (CP-06). */
    @Schema(name = "ChartSliceDto")
    public record ChartSliceDto(
            UUID entityId, String label, String color, int minutes, BigDecimal percentage) {}

    /** Os três gráficos da resposta principal (§10.1). */
    @Schema(name = "DashboardChartsDto")
    public record DashboardChartsDto(
            List<ChartPointDto> dailyMinutes,
            List<ChartSliceDto> byClient,
            List<ChartSliceDto> byCategory) {}

    /**
     * Resposta completa do painel.
     *
     * @param failedBlocks blocos cuja agregação falhou (§10, estado "erro parcial"). Lista vazia é
     *     o caso normal; um bloco listado chega vazio ou zerado e a tela oferece "tentar novamente"
     *     apenas nele — falhar tudo por causa de um gráfico transformaria um problema pequeno em
     *     tela branca (OB-05)
     */
    @Schema(name = "DashboardResponse")
    public record DashboardResponse(
            DashboardPeriodDto period,
            DashboardScope scope,
            QuickStatsDto quickStats,
            List<ContractStatusDto> contracts,
            List<DashboardAlertDto> alerts,
            List<WorkLogSummaryResponse> recentWorkLogs,
            List<TicketDashboardItem> openTickets,
            DashboardChartsDto charts,
            List<String> failedBlocks) {}

    /**
     * Gráfico isolado (§10.2).
     *
     * <p>Exatamente um entre {@code points} e {@code slices} vem preenchido, conforme a forma do
     * tipo; o outro é nulo. Unir os dois em uma lista genérica exigiria um formato que nenhum dos
     * dois gráficos consome.
     */
    @Schema(name = "ChartResponse")
    public record ChartResponse(
            String type, List<ChartPointDto> points, List<ChartSliceDto> slices) {

        public static ChartResponse ofPoints(String type, List<ChartPointDto> points) {
            return new ChartResponse(type, points, null);
        }

        public static ChartResponse ofSlices(String type, List<ChartSliceDto> slices) {
            return new ChartResponse(type, null, slices);
        }
    }
}
