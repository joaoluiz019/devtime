package com.devtime.report;

import com.devtime.report.dto.ReportResponses.ReportAdjustment;
import com.devtime.report.dto.ReportResponses.ReportBalance;
import com.devtime.report.dto.ReportResponses.ReportClient;
import com.devtime.report.dto.ReportResponses.ReportContract;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportFinancial;
import com.devtime.report.dto.ReportResponses.ReportIssuer;
import com.devtime.report.dto.ReportResponses.ReportPeriod;
import java.time.Instant;
import java.util.List;

/**
 * Conteúdo de um relatório de período, <b>independente da fonte</b>.
 *
 * <p>É o que torna verificável o item de §34 "{@code SnapshotReportMapper} e {@code
 * LiveReportMapper} produzem a <b>mesma</b> estrutura": os dois devolvem este tipo, e o serviço que
 * monta a resposta não sabe de onde os dados vieram — apenas se são definitivos ou parciais (§24).
 *
 * <p>Não é um DTO de API: nunca sai pelo controller. Existe para que a igualdade estrutural entre
 * os dois caminhos seja uma propriedade do compilador, e não uma convenção que a próxima alteração
 * quebraria em silêncio.
 *
 * @param snapshotAt instante do congelamento; nulo no caminho ao vivo
 * @param financial nulo sem {@code CONTRACT_VIEW_FINANCIAL} ou sem valor hora (CP-03, CE-R-05)
 */
public record ReportBody(
        ReportIssuer issuer,
        ReportClient client,
        ReportContract contract,
        ReportPeriod period,
        ReportBalance balance,
        List<ReportAdjustment> adjustments,
        ReportFinancial financial,
        List<ReportEntry> entries,
        Instant snapshotAt,
        int reopenCount,
        String currency) {}
