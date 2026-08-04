package com.devtime.report.dto;

import com.devtime.report.domain.ReportGrouping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recorte solicitado, comum aos cinco relatórios (§6 e §7 de reports.md).
 *
 * <p>Um objeto e não onze parâmetros soltos (BR-012). O mesmo tipo serve à consulta ({@code GET
 * /reports/...}) e à exportação ({@code parameters} de §8.1) por exigência de §8.1: "os mesmos
 * aceitos pelo endpoint de consulta correspondente". Dois tipos paralelos divergiriam na primeira
 * vez que um filtro fosse acrescentado só de um lado, e a exportação deixaria de reproduzir a tela.
 */
public final class ReportRequests {

    private ReportRequests() {}

    /**
     * Filtros e opções de composição.
     *
     * <p>Nenhum campo aceita {@code tenantId} (ART-021, BR-090) nem identificador de solicitante: o
     * escopo é derivado do papel por {@code ReportScopePolicy}, e aceitá-lo como parâmetro
     * permitiria a um {@code MEMBER} pedir o consolidado do tenant — a mesma razão pela qual o
     * painel recusa {@code scope} em §10.1.
     *
     * @param includeNonBillable CP-05: os não faturáveis aparecem no detalhamento, marcados, mas
     *     fora de {@code billableMinutes}. Excluí-los é opção do usuário, não default
     * @param includeFinancial pedido do usuário; sujeito a {@code CONTRACT_VIEW_FINANCIAL} — pedir
     *     não concede (CP-03)
     * @param includeUserColumn {@code null} é o {@code auto} de §6: a coluna aparece só quando há
     *     mais de um usuário no resultado
     */
    @Schema(name = "ReportFilters")
    public record ReportFilters(
            ReportGrouping groupBy,
            Boolean includeNonBillable,
            Boolean includeFinancial,
            Boolean includeUserColumn,
            LocalDate from,
            LocalDate to,
            List<UUID> contractIds,
            List<UUID> clientIds,
            List<UUID> categoryIds,
            List<UUID> tagIds,
            List<UUID> userIds,
            Boolean billable) {

        /** §5.1: {@code DATE} é o agrupamento default. */
        public ReportGrouping groupingOrDefault() {
            return groupBy == null ? ReportGrouping.DATE : groupBy;
        }

        /** §6: incluir os não faturáveis é o default. */
        public boolean includeNonBillableOrDefault() {
            return includeNonBillable == null || includeNonBillable;
        }

        /** §6: pedir os valores é o default; a permissão decide se eles saem (CP-03). */
        public boolean includeFinancialOrDefault() {
            return includeFinancial == null || includeFinancial;
        }

        /**
         * CP-05 traduzido em filtro de consulta: {@code null} traz faturáveis e não faturáveis.
         *
         * <p>Devolve {@code false} apenas quando o usuário pediu explicitamente {@code
         * billable=false}; caso contrário, {@code includeNonBillable=false} vira {@code true} — que
         * é "somente faturáveis".
         */
        public Boolean billableFilter() {
            if (billable != null) {
                return billable;
            }
            return includeNonBillableOrDefault() ? null : Boolean.TRUE;
        }

        /** Filtros vazios, para os caminhos que não recebem query alguma. */
        public static ReportFilters empty() {
            return new ReportFilters(
                    null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
