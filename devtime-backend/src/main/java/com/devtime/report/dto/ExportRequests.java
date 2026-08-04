package com.devtime.report.dto;

import com.devtime.report.domain.ExportFormat;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Solicitação de exportação (§8.1 de reports.md).
 *
 * <p>§23: {@code requestedBy} está <b>ausente</b> do contrato. Ele é o autenticado (§13.2);
 * aceitá-lo do cliente permitiria atribuir uma exportação a outra pessoa e destruiria a resposta de
 * §18 para "quem exportou este arquivo".
 */
public final class ExportRequests {

    private ExportRequests() {}

    /**
     * @param parameters os mesmos filtros do endpoint de consulta correspondente, mais o
     *     identificador do recorte quando o tipo o exige (§8.1)
     * @param options página de rosto, gráficos e idioma; não participa da identidade do relatório —
     *     dois PDFs do mesmo período com e sem página de rosto contêm os mesmos dados
     */
    @Schema(name = "ExportRequest")
    public record ExportRequest(
            @NotNull ReportType reportType,
            @NotNull ExportFormat format,
            @Valid ExportParameters parameters,
            @Valid ExportOptions options) {

        public ExportParameters parametersOrEmpty() {
            return parameters == null ? ExportParameters.empty() : parameters;
        }

        public ExportOptions optionsOrDefault() {
            return options == null ? ExportOptions.defaults() : options;
        }
    }

    /**
     * Recorte da exportação.
     *
     * <p>Os três identificadores são mutuamente exclusivos por tipo de relatório, e é {@code
     * ReportService} — não uma validação de formato — quem verifica a combinação, devolvendo {@code
     * DEVTIME-3003} quando o exigido falta (§17.2). Um {@code @AssertTrue} aqui teria de replicar a
     * matriz de tipos e divergiria dela na primeira mudança.
     */
    @Schema(name = "ExportParameters")
    public record ExportParameters(
            UUID contractPeriodId, UUID clientId, UUID ticketId, @Valid ReportFilters filters) {

        public ReportFilters filtersOrEmpty() {
            return filters == null ? ReportFilters.empty() : filters;
        }

        public static ExportParameters empty() {
            return new ExportParameters(null, null, null, null);
        }
    }

    /**
     * Opções de apresentação (§8.1).
     *
     * @param fileName nulo usa o nome composto pelo sistema; quando informado, é sanitizado antes
     *     de virar {@code Content-Disposition} — nome de arquivo é entrada externa (BR-170)
     * @param coverPage página de rosto, apenas PDF
     * @param includeSummaryCharts gráficos de distribuição, apenas PDF
     */
    @Schema(name = "ExportOptions")
    public record ExportOptions(
            @Size(max = 200) String fileName,
            Boolean coverPage,
            Boolean includeSummaryCharts,
            @Size(max = 20) String language) {

        public boolean coverPageOrDefault() {
            return coverPage == null || coverPage;
        }

        public boolean includeSummaryChartsOrDefault() {
            return includeSummaryCharts == null || includeSummaryCharts;
        }

        public static ExportOptions defaults() {
            return new ExportOptions(null, null, null, null);
        }
    }
}
