package com.devtime.dashboard;

import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.dto.ContractResponses.ContractDashboardCard;
import com.devtime.dashboard.domain.ContractSeverity;
import com.devtime.dashboard.dto.DashboardResponses.ContractStatusDto;
import com.devtime.dashboard.dto.DashboardResponses.QuickStatsDto;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogRangeTotals;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Composição das respostas do painel (§24 de specs/010).
 *
 * <p>BR-105/BR-106: sem acesso a banco e sem regra de negócio. A formatação de duração vive aqui —
 * ART-035 exige {@code HH:MM} na apresentação, e a entidade nunca formata.
 *
 * <p>Escrito à mão em vez de gerado por MapStruct porque nenhuma das conversões é campo-a-campo: as
 * fontes são três DTOs de features distintas mais dois cálculos, e um mapeamento declarativo teria
 * mais anotações de exceção do que mapeamentos.
 */
@Component
@RequiredArgsConstructor
public class DashboardMapper {

    private final SeverityCalculator severityCalculator;
    private final ProjectionCalculator projectionCalculator;

    /** ART-035: duração exibida ao usuário é sempre {@code HH:MM}, nunca decimal. */
    public String durationLabel(int minutes) {
        String sign = minutes < 0 ? "-" : "";
        int absolute = Math.abs(minutes);
        return String.format("%s%02d:%02d", sign, absolute / 60, absolute % 60);
    }

    public QuickStatsDto toQuickStats(
            WorkLogRangeTotals today,
            WorkLogRangeTotals week,
            WorkLogRangeTotals period,
            int activeTimerMinutes) {
        return new QuickStatsDto(
                today.netMinutes(),
                durationLabel(today.netMinutes()),
                week.netMinutes(),
                durationLabel(week.netMinutes()),
                period.netMinutes(),
                durationLabel(period.netMinutes()),
                activeTimerMinutes);
    }

    /**
     * Cartão de contrato com severidade e projeção derivadas.
     *
     * <p>Nenhum número de saldo é calculado aqui: {@code available}, {@code consumed}, {@code
     * remaining} e {@code consumptionRate} vêm de {@code BalanceService} (INV-DSH-01). O que esta
     * classe acrescenta é a <b>leitura</b> desses números.
     */
    public ContractStatusDto toContractStatus(
            ContractDashboardCard card,
            PeriodBalanceResponse balance,
            String clientName,
            String clientColor,
            LocalDate today) {
        ContractSeverity severity =
                severityCalculator.calculate(
                        balance.consumptionRate(), card.notificationThresholds());
        ProjectionCalculator.Projection projection =
                projectionCalculator.calculate(
                        balance.startDate(),
                        balance.endDate(),
                        today,
                        balance.consumedMinutes(),
                        balance.availableMinutes());

        return new ContractStatusDto(
                card.contractId(),
                card.code(),
                card.name(),
                clientName,
                clientColor,
                balance.periodId(),
                balance.label(),
                balance.availableMinutes(),
                balance.consumedMinutes(),
                balance.remainingMinutes(),
                balance.consumptionRate(),
                severity,
                daysRemaining(balance.endDate(), today),
                projection.projectedConsumedMinutes(),
                projection.status(),
                balance.isPartial());
    }

    /**
     * CP-02: severidade decrescente, depois {@code daysRemaining} crescente.
     *
     * <p>O que exige ação hoje fica no topo. Ordenar por nome ou por data de criação colocaria o
     * contrato crítico em qualquer lugar da lista, o que anularia o propósito da tela.
     */
    public List<ContractStatusDto> sortByCriticality(List<ContractStatusDto> contracts) {
        return contracts.stream()
                .sorted(
                        Comparator.comparing(ContractStatusDto::severity)
                                .reversed()
                                .thenComparingInt(ContractStatusDto::daysRemaining)
                                .thenComparing(ContractStatusDto::code))
                .toList();
    }

    /** Dias até o fim do período, inclusive: zero no último dia, nunca negativo. */
    int daysRemaining(LocalDate periodEnd, LocalDate today) {
        if (periodEnd == null) {
            return 0;
        }
        long remaining = ChronoUnit.DAYS.between(today, periodEnd);
        return (int) Math.max(0, remaining);
    }
}
