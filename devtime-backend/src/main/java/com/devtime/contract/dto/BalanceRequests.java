package com.devtime.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs de entrada do banco de horas — feature 011 (contracts.md §11 a §13). */
public final class BalanceRequests {

    private BalanceRequests() {}

    /**
     * Ajuste manual do saldo (RN-215, RN-235 a RN-238).
     *
     * <p>{@code appliedBy} e {@code appliedAt} estão <b>ausentes</b>: são sempre do servidor
     * (SG-06). Quem concedeu ou retirou horas é a informação que sustenta o ajuste em uma disputa,
     * e aceitá-la do cliente a tornaria inútil.
     *
     * @param minutes positivo credita, negativo debita; zero é sempre erro de digitação
     * @param reason {@code COURTESY}, {@code CORRECTION}, {@code NEGOTIATED_EXTRA}, {@code
     *     PENALTY}, {@code MIGRATION} ou {@code OTHER}
     * @param justification RN-215 — mínimo de 10 caracteres
     */
    @Schema(name = "AdjustmentRequest")
    public record AdjustmentRequest(
            @NotNull Integer minutes,
            @NotNull com.devtime.contract.domain.AdjustmentReason reason,
            @NotBlank @Size(min = 10, max = 1000) String justification) {

        /** O ajuste precisa ter efeito; {@code 0} nunca é intencional. */
        @jakarta.validation.constraints.AssertTrue(message = "Informe minutos diferentes de zero")
        public boolean isMinutesNonZero() {
            return minutes != null && minutes != 0;
        }
    }

    /**
     * Fechamento do período (RN-239).
     *
     * @param confirmed obrigatório quando o fechamento é antecipado; confirmar congela um período
     *     que ainda está recebendo horas
     * @param earlyClosingReason contexto do fechamento antecipado, registrado em auditoria
     */
    @Schema(name = "ClosePeriodRequest")
    public record ClosePeriodRequest(
            boolean confirmed, @Size(max = 1000) String earlyClosingReason) {}

    /**
     * Reabertura do período (RN-242).
     *
     * <p>A justificativa é obrigatória porque a reabertura altera um relatório <b>já entregue</b>.
     * Sem o motivo registrado, a operação é indefensável em disputa contratual.
     */
    @Schema(name = "ReopenPeriodRequest")
    public record ReopenPeriodRequest(@NotBlank @Size(min = 10, max = 1000) String reason) {}
}
