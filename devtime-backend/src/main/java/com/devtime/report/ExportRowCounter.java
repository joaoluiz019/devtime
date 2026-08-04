package com.devtime.report;

import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ExportRequests.ExportParameters;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Contagem de linhas do resultado filtrado, antes de decidir síncrono × assíncrono (RN-706).
 *
 * <p><b>Conta sem materializar</b> (CP-13, §20): uma exportação de 50.000 linhas contada por {@code
 * size()} esgotaria a memória exatamente no passo que existe para evitá-lo.
 *
 * <p>Aplica o escopo e o intervalo <b>antes</b> de contar, e é por isso que a decisão de modo não
 * pode ser tomada sobre um número aproximado: um {@code MEMBER} cujo escopo reduz o resultado a 300
 * linhas não deveria esperar por uma exportação assíncrona só porque o tenant tem 50.000 registros.
 */
@Component
@RequiredArgsConstructor
public class ExportRowCounter {

    private final ReportScopePolicy scopePolicy;
    private final ReportPeriodValidator periodValidator;
    private final ReportEntryLoader entryLoader;

    public long count(ReportType reportType, ExportParameters parameters) {
        ReportFilters filters = parameters.filtersOrEmpty();

        // CE-P-10: na exportação o escopo é mais restritivo que na consulta, e é verificado aqui —
        // antes de a execução ser persistida, para que um pedido recusado não deixe registro de
        // uma exportação que nunca existiu.
        Optional<UUID> scope = scopePolicy.resolveForExport(reportType, filters);

        return switch (reportType) {
            case CONTRACT_PERIOD ->
                    entryLoader.count(
                            entryLoader.filterFor(
                                    null,
                                    parameters.contractPeriodId(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    filters,
                                    scope.orElse(null)));
            case TICKET_DETAIL ->
                    entryLoader.count(
                            entryLoader.filterFor(
                                    null,
                                    null,
                                    null,
                                    parameters.ticketId(),
                                    null,
                                    null,
                                    filters,
                                    scope.orElse(null)));
            case CLIENT_SUMMARY -> countInRange(parameters.clientId(), filters, scope);
            case TIMESHEET, PRODUCTIVITY -> countInRange(null, filters, scope);
        };
    }

    /** Os três tipos com intervalo livre validam RN-705 antes de tocar o banco (SG-08). */
    private long countInRange(UUID clientId, ReportFilters filters, Optional<UUID> scope) {
        periodValidator.assertValidRange(filters.from(), filters.to());
        return entryLoader.count(
                entryLoader.filterFor(
                        null,
                        null,
                        clientId,
                        null,
                        filters.from(),
                        filters.to(),
                        filters,
                        scope.orElse(null)));
    }
}
