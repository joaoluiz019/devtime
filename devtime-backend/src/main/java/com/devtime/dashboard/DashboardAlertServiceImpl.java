package com.devtime.dashboard;

import com.devtime.contract.dto.ContractResponses.ContractDashboardCard;
import com.devtime.dashboard.domain.ContractSeverity;
import com.devtime.dashboard.dto.DashboardResponses.ContractStatusDto;
import com.devtime.dashboard.dto.DashboardResponses.DashboardAlertDto;
import com.devtime.shared.time.TenantClock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alertas do estado presente (ver {@link DashboardAlertService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardAlertServiceImpl implements DashboardAlertService {

    /** RN-605: o lembrete de fechamento aparece a 3 dias do fim do período. */
    private static final int PERIOD_CLOSING_DAYS = 3;

    /** RN-606: o aviso de contrato terminando aparece a 15 dias do fim da vigência. */
    private static final int CONTRACT_ENDING_DAYS = 15;

    private static final String ENTITY_CONTRACT_PERIOD = "CONTRACT_PERIOD";
    private static final String ENTITY_CONTRACT = "CONTRACT";

    private final SeverityCalculator severityCalculator;
    private final TenantClock clock;

    @Override
    @PreAuthorize(
            "hasPermission(null, 'DASHBOARD_VIEW_ANY') or hasPermission(null,"
                    + " 'DASHBOARD_VIEW_OWN')")
    public List<DashboardAlertDto> deriveFrom(
            List<ContractStatusDto> contracts, Map<UUID, ContractDashboardCard> cardsById) {
        LocalDate today = clock.today(); // RN-009
        List<DashboardAlertDto> alerts = new ArrayList<>();

        for (ContractStatusDto contract : contracts) {
            ContractDashboardCard card = cardsById.get(contract.contractId());
            if (card == null) {
                continue;
            }
            // CX-05 / CE-10: HOURLY_OPEN tem available = 0 e nunca produz alerta de consumo. Sem
            // teto não existe "perto do limite", e alertar seria ruído permanente.
            if (contract.availableMinutes() > 0) {
                overageAlert(contract).ifPresent(alerts::add);
                usageAlert(contract, card).ifPresent(alerts::add);
            }
            periodClosingAlert(contract).ifPresent(alerts::add);
            contractEndingAlert(contract, card, today).ifPresent(alerts::add);
        }

        // O mais crítico primeiro, pela mesma razão de CP-02: a lista existe para dirigir a
        // atenção.
        return alerts.stream()
                .sorted(Comparator.comparing(DashboardAlertDto::severity).reversed())
                .toList();
    }

    /** RN-604: consumo acima de 100% tem impacto financeiro direto. */
    private java.util.Optional<DashboardAlertDto> overageAlert(ContractStatusDto contract) {
        if (contract.severity() != ContractSeverity.CRITICAL) {
            return java.util.Optional.empty();
        }
        int overage = Math.max(0, contract.consumedMinutes() - contract.availableMinutes());
        return java.util.Optional.of(
                new DashboardAlertDto(
                        "CONTRACT_OVERAGE",
                        ContractSeverity.CRITICAL,
                        "%s excedeu o saldo em %d minutos".formatted(contract.name(), overage),
                        ENTITY_CONTRACT_PERIOD,
                        contract.periodId()));
    }

    /**
     * RN-602: limiar de consumo atingido.
     *
     * <p>O {@code type} carrega o limiar do <b>contrato</b> ({@code CONTRACT_USAGE_70} para um
     * contrato configurado com {@code [70, 90]}), e não um valor fixo: é o mesmo identificador que
     * RN-603 usa no {@code dedupeKey} da notificação correspondente. Nomeá-lo de outra forma faria
     * a tela e o e-mail descreverem o mesmo evento com nomes diferentes (R-03).
     */
    private java.util.Optional<DashboardAlertDto> usageAlert(
            ContractStatusDto contract, ContractDashboardCard card) {
        if (contract.severity() == ContractSeverity.OK
                || contract.severity() == ContractSeverity.CRITICAL) {
            return java.util.Optional.empty();
        }
        return severityCalculator
                .highestReachedThreshold(contract.consumptionRate(), card.notificationThresholds())
                .map(
                        threshold ->
                                new DashboardAlertDto(
                                        "CONTRACT_USAGE_" + threshold,
                                        contract.severity(),
                                        "%s atingiu %s%% do saldo"
                                                .formatted(
                                                        contract.name(),
                                                        contract.consumptionRate()
                                                                .stripTrailingZeros()
                                                                .toPlainString()),
                                        ENTITY_CONTRACT_PERIOD,
                                        contract.periodId()));
    }

    /** RN-605: período próximo do fechamento. Só faz sentido enquanto ele ainda recebe horas. */
    private java.util.Optional<DashboardAlertDto> periodClosingAlert(ContractStatusDto contract) {
        if (!contract.isPartial() || contract.daysRemaining() > PERIOD_CLOSING_DAYS) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                new DashboardAlertDto(
                        "PERIOD_CLOSING",
                        ContractSeverity.INFO,
                        "O período %s de %s fecha em %d dia(s)"
                                .formatted(
                                        contract.periodLabel(),
                                        contract.name(),
                                        contract.daysRemaining()),
                        ENTITY_CONTRACT_PERIOD,
                        contract.periodId()));
    }

    /** RN-606: contrato próximo do fim da vigência. */
    private java.util.Optional<DashboardAlertDto> contractEndingAlert(
            ContractStatusDto contract, ContractDashboardCard card, LocalDate today) {
        LocalDate endDate = card.contractEndDate();
        if (endDate == null || endDate.isBefore(today)) {
            return java.util.Optional.empty();
        }
        long daysToEnd = ChronoUnit.DAYS.between(today, endDate);
        if (daysToEnd > CONTRACT_ENDING_DAYS) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                new DashboardAlertDto(
                        "CONTRACT_ENDING",
                        ContractSeverity.WARNING,
                        "%s termina em %d dia(s)".formatted(contract.name(), daysToEnd),
                        ENTITY_CONTRACT,
                        contract.contractId()));
    }
}
