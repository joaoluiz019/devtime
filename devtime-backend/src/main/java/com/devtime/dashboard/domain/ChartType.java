package com.devtime.dashboard.domain;

import java.util.Arrays;
import java.util.Locale;
import lombok.Getter;

/**
 * Os seis gráficos de {@code GET /dashboard/chart/{type}} (§10.2 de reports.md).
 *
 * <p>{@code byContract}, {@code billableRatio} e {@code consumptionTrend} não aparecem na resposta
 * principal: existem para que a tela ofereça troca de visualização sem alteração de contrato de API
 * (OB-08).
 *
 * <p>O valor externo é kebab-case por ART-071; o enum guarda a tradução em um lugar só para que
 * nenhum controller monte a correspondência por conta própria.
 */
@Getter
public enum ChartType {

    /** Série diária de minutos. Sempre 30 pontos, com zeros visíveis (CP-04). */
    DAILY_MINUTES("daily-minutes", ChartShape.POINTS),
    BY_CLIENT("by-client", ChartShape.SLICES),
    BY_CATEGORY("by-category", ChartShape.SLICES),
    BY_CONTRACT("by-contract", ChartShape.SLICES),

    /** Faturável × não faturável no intervalo. */
    BILLABLE_RATIO("billable-ratio", ChartShape.SLICES),

    /**
     * Consumo acumulado dia a dia no intervalo.
     *
     * <p><b>Lacuna de documentação registrada:</b> §14 de specs/010 e §10.2 de reports.md nomeiam o
     * tipo sem definir o que ele agrega. A leitura implementada — minutos faturáveis acumulados por
     * dia — é a única compatível com o nome e com a forma {@code points[]} de §23, e está declarada
     * aqui para poder ser corrigida em um lugar só quando a definição chegar.
     */
    CONSUMPTION_TREND("consumption-trend", ChartShape.POINTS);

    /** Forma da resposta: §23 prevê {@code points[]} <b>ou</b> {@code slices[]}, nunca ambos. */
    public enum ChartShape {
        POINTS,
        SLICES
    }

    private final String externalName;
    private final ChartShape shape;

    ChartType(String externalName, ChartShape shape) {
        this.externalName = externalName;
        this.shape = shape;
    }

    /**
     * Resolve o tipo pelo nome externo.
     *
     * @throws DashboardExceptions.DashboardValidationException quando o nome não corresponde a
     *     nenhum dos seis
     */
    public static ChartType fromExternalName(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.externalName.equals(normalized))
                .findFirst()
                .orElseThrow(() -> DashboardExceptions.invalidChartType(value));
    }
}
