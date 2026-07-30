package com.devtime.contract.dto;

import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.OveragePolicy;
import com.devtime.contract.domain.RolloverPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de entrada da feature 004 (contracts.md §5, §6 e §8; spec §23).
 *
 * <p>{@code status} está ausente de todos (SG-05, ME-05): a situação muda apenas por endpoint de
 * ação. {@code type} está ausente da atualização porque é imutável fora de {@code DRAFT} (RN-206) —
 * um campo ausente do contrato é barreira mais forte que um campo validado.
 */
public final class ContractRequests {

    private ContractRequests() {}

    /** contracts.md §5. */
    @Schema(name = "ContractCreateRequest")
    public record ContractCreateRequest(
            @NotNull UUID clientId,
            @Size(max = 30) String code,
            @NotBlank @Size(min = 2, max = 150) String name,
            @Size(max = 4000) String description,
            @NotNull ContractType type,
            @Min(1) @Max(44640) Integer monthlyMinutes,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Min(1) @Max(28) Integer billingDay,
            RolloverPolicy rolloverPolicy,
            @Min(0) Integer rolloverCapMinutes,
            @Min(0) Integer rolloverExpiryPeriods,
            OveragePolicy overagePolicy,
            @DecimalMin("0.0") BigDecimal hourlyRate,
            @DecimalMin("0.0") BigDecimal overageRate,
            @Size(min = 3, max = 3) String currency,
            Boolean autoRenew,
            Boolean prorateFirstPeriod,
            @Size(min = 1, max = 5) List<@Min(1) @Max(500) Integer> notificationThresholds,
            UUID defaultCategoryId,
            @Size(max = 4000) String notes) {

        /** BR-103 / RN-204: validação cruzada de datas no próprio record. */
        @AssertTrue(message = "endDate deve ser maior ou igual a startDate")
        public boolean isDateRangeValid() {
            return endDate == null || startDate == null || !endDate.isBefore(startDate);
        }

        /** INV-CTR-02: o pacote mensal é obrigatório no contrato de horas mensais. */
        @AssertTrue(message = "monthlyMinutes é obrigatório em MONTHLY_HOURS")
        public boolean isMonthlyMinutesConsistent() {
            return type != ContractType.MONTHLY_HOURS || monthlyMinutes != null;
        }

        /** INV-CTR-03: horas abertas não têm saldo (CX-08). */
        @AssertTrue(message = "HOURLY_OPEN não aceita monthlyMinutes nem rollover")
        public boolean isHourlyOpenCoherent() {
            if (type != ContractType.HOURLY_OPEN) {
                return true;
            }
            return monthlyMinutes == null
                    && (rolloverPolicy == null || rolloverPolicy == RolloverPolicy.NONE);
        }

        /** INV-CTR-04. */
        @AssertTrue(message = "rolloverCapMinutes é obrigatório na política CAPPED")
        public boolean isRolloverCapConsistent() {
            return rolloverPolicy != RolloverPolicy.CAPPED || rolloverCapMinutes != null;
        }
    }

    /**
     * contracts.md §7.
     *
     * <p>{@code applyToCurrentPeriod} materializa a confirmação exigida por RN-207 para que a
     * alteração de {@code monthlyMinutes} alcance o período aberto (CE-CT-02).
     */
    @Schema(name = "ContractUpdateRequest")
    public record ContractUpdateRequest(
            @NotBlank @Size(min = 2, max = 150) String name,
            @Size(max = 4000) String description,
            @Min(1) @Max(44640) Integer monthlyMinutes,
            LocalDate endDate,
            @Min(1) @Max(28) Integer billingDay,
            RolloverPolicy rolloverPolicy,
            @Min(0) Integer rolloverCapMinutes,
            @Min(0) Integer rolloverExpiryPeriods,
            OveragePolicy overagePolicy,
            @DecimalMin("0.0") BigDecimal hourlyRate,
            @DecimalMin("0.0") BigDecimal overageRate,
            Boolean autoRenew,
            @Size(min = 1, max = 5) List<@Min(1) @Max(500) Integer> notificationThresholds,
            UUID defaultCategoryId,
            @Size(max = 4000) String notes,
            Boolean applyToCurrentPeriod,
            @NotNull Long version) {}

    /** contracts.md §6: cálculo puro, sem {@code clientId} e sem persistência. */
    @Schema(name = "PeriodPreviewRequest")
    public record PeriodPreviewRequest(
            @NotNull ContractType type,
            @Min(1) @Max(44640) Integer monthlyMinutes,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull @Min(1) @Max(28) Integer billingDay,
            Boolean prorateFirstPeriod,
            @Min(1) @Max(12) Integer periodsCount) {

        @AssertTrue(message = "endDate deve ser maior ou igual a startDate")
        public boolean isDateRangeValid() {
            return endDate == null || startDate == null || !endDate.isBefore(startDate);
        }
    }

    /**
     * Corpo das transições (contracts.md §8.2 a §8.5).
     *
     * @param reason justificativa; obrigatória em suspensão e cancelamento, com ≥ 10 caracteres
     * @param endDate data de término, usada em {@code /end}
     * @param confirmation confirmação textual exigida no cancelamento
     */
    @Schema(name = "ContractTransitionRequest")
    public record ContractTransitionRequest(
            @Size(max = 1000) String reason, LocalDate endDate, String confirmation) {}
}
