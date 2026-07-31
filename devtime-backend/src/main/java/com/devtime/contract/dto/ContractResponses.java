package com.devtime.contract.dto;

import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.OveragePolicy;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.domain.RolloverPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs de saída da feature 004 (contracts.md §5, §7, §8 e §12.2). */
public final class ContractResponses {

    private ContractResponses() {}

    /** Cliente achatado na resposta do contrato (contracts.md §7). */
    @Schema(name = "ContractClientResponse")
    public record ContractClientResponse(UUID id, String name, String color) {}

    /**
     * Período do contrato.
     *
     * <p>Os campos de saldo ({@code availableMinutes}, {@code remainingMinutes}, {@code
     * consumptionRate}) pertencem a {@code 011-bank-hours} e não são calculados aqui: a fronteira
     * de §4 da spec é explícita — {@code 004} nunca calcula saldo.
     */
    @Schema(name = "ContractPeriodResponse")
    public record ContractPeriodResponse(
            UUID id,
            int sequence,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            PeriodStatus status,
            int contractedMinutes,
            int carriedInMinutes,
            int adjustmentMinutes,
            int consumedMinutes,
            int nonBillableMinutes,
            String currency) {}

    /**
     * Referência enxuta do contrato para features consumidoras (AR-02).
     *
     * <p>{@code status} é {@code String} e não o enum: {@code ContractStatus} pertence ao domínio
     * de {@code 004}, e AR-02 proíbe que {@code 007} ou {@code 008} dependam dele. A decisão de
     * negócio que essas features realmente precisam — se o contrato aceita registro de horas
     * (RN-306) — chega calculada em {@code acceptsWorkLogs}, tomada por quem é dono da regra.
     *
     * @param acceptsWorkLogs RN-306: verdadeiro apenas em {@code ACTIVE} e {@code SUSPENDED}
     */
    @Schema(name = "ContractRefResponse")
    public record ContractRefResponse(
            UUID id,
            String code,
            String name,
            String status,
            boolean acceptsWorkLogs,
            ContractClientResponse client) {}

    /** Item de {@code periodsPreview} (contracts.md §5 e §6). */
    @Schema(name = "PeriodPreviewItem")
    public record PeriodPreviewItem(
            int sequence,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            int contractedMinutes,
            boolean prorated,
            String prorationBasis) {}

    @Schema(name = "PeriodPreviewResponse")
    public record PeriodPreviewResponse(List<PeriodPreviewItem> periodsPreview) {}

    /**
     * Detalhe do contrato.
     *
     * <p>SG-03: os campos monetários são nulos quando o requisitante não possui {@code
     * CONTRACT_VIEW_FINANCIAL} — a omissão ocorre no backend, nunca apenas na interface.
     */
    @Schema(name = "ContractResponse")
    public record ContractResponse(
            UUID id,
            String code,
            String name,
            String description,
            ContractClientResponse client,
            ContractType type,
            ContractStatus status,
            Integer monthlyMinutes,
            LocalDate startDate,
            LocalDate endDate,
            int billingDay,
            RolloverPolicy rolloverPolicy,
            Integer rolloverCapMinutes,
            int rolloverExpiryPeriods,
            OveragePolicy overagePolicy,
            BigDecimal hourlyRate,
            BigDecimal overageRate,
            String currency,
            boolean autoRenew,
            boolean prorateFirstPeriod,
            List<Integer> notificationThresholds,
            UUID defaultCategoryId,
            String notes,
            ContractPeriodResponse currentPeriod,
            List<PeriodPreviewItem> periodsPreview,
            long version,
            List<String> availableTransitions,
            List<String> availableActions) {}

    /** Item da listagem (contracts.md §7) — projeção, nunca a entidade (BR-107). */
    @Schema(name = "ContractListItemResponse")
    public record ContractListItemResponse(
            UUID id,
            String code,
            String name,
            ContractClientResponse client,
            ContractType type,
            ContractStatus status,
            Integer monthlyMinutes,
            LocalDate startDate,
            LocalDate endDate,
            ContractPeriodResponse currentPeriod,
            long version) {}

    /** contracts.md §8.1. */
    @Schema(name = "ContractActivationResponse")
    public record ContractActivationResponse(
            ContractStatus status, ContractPeriodResponse firstPeriod) {}

    /** contracts.md §8.3: períodos gerados para preservar a contiguidade após a suspensão. */
    @Schema(name = "ContractTransitionResponse")
    public record ContractTransitionResponse(
            ContractStatus status,
            List<ContractPeriodResponse> generatedPeriods,
            ContractPeriodResponse truncatedPeriod) {}

    /**
     * Série histórica dos períodos (contracts.md §12.2).
     *
     * <p>Os agregados de consumo e a tendência dependem de horas registradas, que chegam com {@code
     * 008}; os campos existem e refletem os valores correntes dos períodos.
     */
    @Schema(name = "ContractHistoryResponse")
    public record ContractHistoryResponse(
            UUID contractId,
            List<ContractHistoryPeriod> periods,
            ContractHistoryAggregates aggregates) {}

    @Schema(name = "ContractHistoryPeriod")
    public record ContractHistoryPeriod(
            int sequence,
            String label,
            PeriodStatus status,
            int contractedMinutes,
            int carriedInMinutes,
            int adjustmentMinutes,
            int consumedMinutes,
            int remainingMinutes,
            int overageMinutes,
            int carriedOutMinutes) {}

    @Schema(name = "ContractHistoryAggregates")
    public record ContractHistoryAggregates(
            int periodsCount,
            int periodsWithOverage,
            int totalOverageMinutes,
            int totalCarriedOutMinutes) {}

    /** clients.md §8: resumo consolidado do cliente, servido por esta feature (ver §22.1). */
    @Schema(name = "ClientContractSummaryResponse")
    public record ClientContractSummaryResponse(
            UUID clientId,
            String currency,
            SummaryTotals totals,
            List<ContractHistoryPeriod> history,
            List<ContractSummaryByContract> byContract) {}

    @Schema(name = "SummaryTotals")
    public record SummaryTotals(
            int contractedMinutes,
            int consumedMinutes,
            int nonBillableMinutes,
            int remainingMinutes,
            int overageMinutes) {}

    @Schema(name = "ContractSummaryByContract")
    public record ContractSummaryByContract(
            UUID contractId, String code, String name, int minutes) {}
}
