package com.devtime.dashboard.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;

/**
 * Exceções do painel (§27 de specs/010).
 *
 * <p>BR-063: fábricas nomeadas pela regra, nunca construtor genérico — é o que mantém código,
 * mensagem e detalhes coerentes entre si.
 */
public final class DashboardExceptions {

    /** RN-705: teto de 366 dias para o intervalo personalizado. */
    public static final int MAX_CUSTOM_RANGE_DAYS = 366;

    private DashboardExceptions() {}

    /** RN-705 / {@code DEVTIME-3001}: intervalo personalizado acima de 366 dias. */
    public static DateRangeTooLargeException dateRangeTooLarge(long requestedDays) {
        return new DateRangeTooLargeException(requestedDays);
    }

    /** §17.1: {@code period=CUSTOM} exige {@code from} e {@code to}. */
    public static DashboardValidationException customRangeIncomplete() {
        return new DashboardValidationException(
                Map.of("period", "CUSTOM", "required", "from,to"),
                "Informe o período personalizado");
    }

    /** §17.1: {@code to} anterior a {@code from}. */
    public static DashboardValidationException invertedRange() {
        return new DashboardValidationException(
                Map.of("constraint", "to >= from"), "Data final anterior à inicial");
    }

    /** §10.2: tipo de gráfico fora dos seis publicados. */
    public static DashboardValidationException invalidChartType(String requested) {
        return new DashboardValidationException(
                Map.of("field", "type", "requested", String.valueOf(requested)),
                "Tipo de gráfico inválido");
    }

    /** RN-705 (§27 de specs/010). */
    public static class DateRangeTooLargeException extends BusinessRuleException {

        DateRangeTooLargeException(long requestedDays) {
            super(
                    ErrorCode.DATE_RANGE_EXCEEDED,
                    Map.of("requestedDays", requestedDays, "maxDays", MAX_CUSTOM_RANGE_DAYS),
                    "Intervalo de datas excede o máximo permitido");
        }
    }

    /**
     * Parâmetro de consulta inválido (§17.1, camada 1).
     *
     * <p><b>Divergência registrada:</b> §12 de specs/010 atribui {@code 422} ao tipo de gráfico
     * inválido e ao intervalo personalizado incompleto, mas §17.1 do mesmo documento classifica os
     * dois como validação de <b>formato</b>, cuja resposta é {@code 400}. Vale §17.1, por ser a
     * seção específica de validação, e porque {@code DEVTIME-2000} já está registrado como {@code
     * 400} no catálogo compartilhado — alterá-lo mudaria o significado de uma condição já integrada
     * por outras features.
     */
    public static class DashboardValidationException extends BusinessRuleException {

        DashboardValidationException(Map<String, Object> details, String message) {
            super(ErrorCode.VALIDATION_FAILED, details, message);
        }
    }
}
