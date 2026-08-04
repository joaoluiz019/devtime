package com.devtime.report.render;

import com.devtime.report.domain.ExportFormat;
import com.devtime.report.dto.ExportRequests.ExportOptions;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportGroup;
import com.devtime.report.dto.ReportResponses.ReportSummarySlice;
import com.devtime.shared.time.TenantClock;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextRenderer;

/**
 * PDF do relatório (§9.1 de reports.md, ADR-036 RP-07).
 *
 * <p><b>O PDF é o entregável do produto</b> (PV-05, RP-04): é o documento anexado ao e-mail de
 * cobrança, e um relatório que parece exportação de planilha faz o cliente duvidar do
 * profissionalismo de quem o enviou, independentemente de os números estarem corretos.
 *
 * <p><b>Determinismo</b> (RN-708, PDF-08). Três coisas o garantem: a fonte única (o snapshot), a
 * ordenação normativa (§6.3) e o produtor fixo declarado abaixo. Sem o produtor fixo, a versão da
 * biblioteca entraria nos metadados e dois PDFs do mesmo período gerados antes e depois de uma
 * atualização de dependência divergiriam em bytes sem que nenhum dado tivesse mudado. O que
 * legitimamente varia entre duas gerações é o carimbo de emissão — {@code issueId}, {@code
 * generatedAt} e a data de criação do arquivo.
 *
 * <p><b>Limitação registrada:</b> Flying Saucer monta a árvore do documento antes de paginar, então
 * o PDF <b>não</b> é escrito em fluxo como o XLSX. O que limita a memória aqui é o limiar de 5.000
 * linhas de RN-706, acima do qual a geração é assíncrona e serializada pelo worker. A pendência
 * está no CHANGELOG desta sprint.
 */
@Component
@RequiredArgsConstructor
public class PdfRenderer implements ReportRenderer {

    /** Fixo, e não a versão da biblioteca: é o que torna os metadados estáveis (RN-708). */
    private static final String PDF_PRODUCER = "DevTime";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TenantClock clock;

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public void render(RenderableReport report, ExportOptions options, OutputStream output) {
        ITextRenderer renderer = new ITextRenderer();
        renderer.setPDFProducer(PDF_PRODUCER);
        renderer.setPDFCreator(PDF_PRODUCER);
        renderer.setDocumentFromString(html(report, options));
        renderer.layout();
        try {
            renderer.createPDF(output);
        } catch (Exception e) {
            // ER-02 permite o catch amplo aqui: `createPDF` declara a exceção do motor de PDF, que
            // é detalhe de integração. Ela é traduzida para uma falha do domínio, e nunca chega ao
            // usuário com a mensagem crua (BR-092, CE-R-11).
            throw new PdfRenderingException(e);
        }
    }

    /** Falha do motor de PDF, traduzida na fronteira (CE-R-11). */
    public static class PdfRenderingException extends RuntimeException {

        PdfRenderingException(Throwable cause) {
            super("Falha ao renderizar o PDF do relatório", cause);
        }
    }

    private String html(RenderableReport report, ExportOptions options) {
        StringBuilder html = new StringBuilder(8192);
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<html><head><meta charset=\"UTF-8\"/><style>")
                .append(css(report))
                .append("</style></head><body>");

        if (report.isPartial()) {
            // PDF-06 e CP-03: a marcação é repetida em todas as páginas por `position: fixed`, e é
            // proeminente. Um PDF parcial exibido discretamente será impresso e enviado ao cliente
            // como se fosse final — é exatamente o cenário que RN-702 existe para evitar.
            html.append("<div class=\"watermark\">")
                    .append(escape(partialMarker(report)))
                    .append("</div>");
        }

        if (options != null && options.coverPageOrDefault()) {
            html.append(coverPage(report));
        }
        html.append(balanceSection(report));
        html.append(detailSection(report));
        html.append(summarySection(report));
        html.append(totalsSection(report));
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * PDF-01, PDF-02, PDF-03, PDF-05, PDF-07 e PDF-09 traduzidos em CSS.
     *
     * <p>Retrato quando o detalhamento cabe em até 6 colunas, paisagem acima disso (PDF-01). As
     * colunas do detalhamento são 8 no PDF — menos que as 13 do XLSX, porque impressão em A4 não
     * comporta descrição legível junto de tudo o mais.
     */
    private String css(RenderableReport report) {
        return """
               @page {
                 size: A4 landscape;
                 margin: 1.5cm 1.2cm 2cm 1.2cm;
                 @bottom-center {
                   content: "Página " counter(page) " de " counter(pages) " · Emissão %s";
                   font-size: 8pt; color: #555;
                 }
               }
               body { font-family: Helvetica, sans-serif; font-size: 9pt; color: #111; }
               h1 { font-size: 16pt; margin: 0 0 4pt 0; }
               h2 { font-size: 11pt; margin: 12pt 0 4pt 0; border-bottom: 1pt solid #ccc; }
               table { width: 100%%; border-collapse: collapse; }
               thead { display: table-header-group; }
               th { background: #eef; text-align: left; padding: 3pt; font-size: 9pt; }
               td { padding: 3pt; border-bottom: 0.5pt solid #ddd; vertical-align: top; }
               td.num { text-align: right; }
               .meta { font-size: 8pt; color: #444; }
               .group { background: #f5f5f5; font-weight: bold; }
               .watermark {
                 position: fixed; top: 40%%; left: 12%%;
                 font-size: 42pt; color: #d00; opacity: 0.25;
                 -fs-transform: rotate(-30deg);
               }
               """
                .formatted(escape(report.issueId()));
    }

    private String coverPage(RenderableReport report) {
        StringBuilder cover = new StringBuilder();
        cover.append("<h1>").append(escape(titleOf(report))).append("</h1>");
        cover.append("<div class=\"meta\">")
                .append("Emissão ")
                .append(escape(report.issueId()))
                .append(" · gerado em ")
                .append(escape(stamp(report)))
                .append(
                        report.generatedByName() == null
                                ? ""
                                : " · por " + escape(report.generatedByName()))
                .append("</div>");

        if (report.issuer() != null) {
            cover.append("<h2>Emissor</h2><div>")
                    .append(escape(report.issuer().name()))
                    .append(line(report.issuer().legalName()))
                    .append(line(report.issuer().documentNumber()))
                    .append("</div>");
        }
        if (report.client() != null) {
            cover.append("<h2>Cliente</h2><div>")
                    .append(escape(report.client().name()))
                    .append(line(report.client().legalName()))
                    .append(line(report.client().documentNumber()))
                    .append("</div>");
        }
        if (report.contract() != null) {
            cover.append("<h2>Contrato</h2><div>")
                    .append(escape(report.contract().code()))
                    .append(" — ")
                    .append(escape(report.contract().name()))
                    .append("</div>");
        }
        if (report.period() != null) {
            cover.append("<h2>Período</h2><div>")
                    .append(escape(report.period().label()))
                    .append(" · ")
                    .append(escape(String.valueOf(report.period().startDate())))
                    .append(" a ")
                    .append(escape(String.valueOf(report.period().endDate())))
                    .append("</div>");
        }
        return cover.toString();
    }

    private String balanceSection(RenderableReport report) {
        if (report.balance() == null) {
            return "";
        }
        StringBuilder section = new StringBuilder("<h2>Saldo</h2><table>");
        section.append(balanceRow("Contratado", report.balance().contractedMinutes()));
        section.append(balanceRow("Transportado", report.balance().carriedInMinutes()));
        section.append(balanceRow("Ajustes", report.balance().adjustmentMinutes()));
        section.append(balanceRow("Disponível", report.balance().availableMinutes()));
        section.append(balanceRow("Consumido", report.balance().consumedMinutes()));
        section.append(balanceRow("Não faturável", report.balance().nonBillableMinutes()));
        section.append(balanceRow("Restante", report.balance().remainingMinutes()));
        section.append(balanceRow("Excedente", report.balance().overageMinutes()));
        section.append("</table>");
        return section.toString();
    }

    private String balanceRow(String label, int minutes) {
        return "<tr><td>%s</td><td class=\"num\">%d</td></tr>".formatted(escape(label), minutes);
    }

    /**
     * PDF-03: {@code thead} repetido em cada página; PDF-05: descrição quebrada, nunca truncada.
     */
    private String detailSection(RenderableReport report) {
        if (report.groups().isEmpty()) {
            return "";
        }
        boolean includeValue = report.hasFinancialColumn();
        ZoneId zone = clock.tenantZone();

        StringBuilder section = new StringBuilder("<h2>Detalhamento</h2><table><thead><tr>");
        section.append("<th>Data</th><th>Início</th><th>Fim</th><th>Ticket</th>")
                .append("<th>Categoria</th><th>Usuário</th><th>Descrição</th><th>Duração</th>");
        if (includeValue) {
            section.append("<th>Valor</th>");
        }
        section.append("</tr></thead><tbody>");

        int columns = includeValue ? 9 : 8;
        for (ReportGroup group : report.groups()) {
            if (group.label() != null) {
                section.append("<tr class=\"group\"><td colspan=\"")
                        .append(columns)
                        .append("\">")
                        .append(escape(group.label()))
                        .append(" — ")
                        .append(escape(group.durationLabel()))
                        .append("</td></tr>");
            }
            for (ReportEntry entry : group.entries()) {
                section.append(detailRow(entry, includeValue, zone));
            }
        }
        section.append("</tbody></table>");
        return section.toString();
    }

    private String detailRow(ReportEntry entry, boolean includeValue, ZoneId zone) {
        StringBuilder row = new StringBuilder("<tr>");
        row.append(cell(String.valueOf(entry.workDate())))
                .append(cell(time(entry.startedAt(), zone)))
                .append(cell(time(entry.endedAt(), zone)))
                .append(cell(entry.ticketKey()))
                .append(cell(entry.categoryName()))
                .append(cell(entry.userName()))
                .append(cell(entry.description()))
                .append("<td class=\"num\">")
                .append(escape(entry.durationLabel()))
                .append("</td>");
        if (includeValue) {
            row.append("<td class=\"num\">")
                    .append(entry.value() == null ? "" : escape(entry.value().toPlainString()))
                    .append("</td>");
        }
        return row.append("</tr>").toString();
    }

    private String summarySection(RenderableReport report) {
        if (report.summaries() == null || report.summaries().byCategory().isEmpty()) {
            return "";
        }
        StringBuilder section =
                new StringBuilder("<h2>Distribuição por categoria</h2><table><thead><tr>")
                        .append("<th>Categoria</th><th>Minutos</th><th>%</th>")
                        .append("</tr></thead><tbody>");
        for (ReportSummarySlice slice : report.summaries().byCategory()) {
            section.append("<tr>")
                    .append(cell(slice.label()))
                    .append("<td class=\"num\">")
                    .append(slice.minutes())
                    .append("</td><td class=\"num\">")
                    .append(escape(String.valueOf(slice.percentage())))
                    .append("</td></tr>");
        }
        return section.append("</tbody></table>").toString();
    }

    private String totalsSection(RenderableReport report) {
        if (report.totals() == null) {
            return "";
        }
        return new StringBuilder("<h2>Totais</h2><table>")
                .append(balanceRow("Registros", report.totals().entriesCount()))
                .append(balanceRow("Dias com registro", report.totals().distinctDays()))
                .append(balanceRow("Tickets", report.totals().distinctTickets()))
                .append("<tr><td>Tempo total</td><td class=\"num\">")
                .append(escape(report.totals().durationLabel()))
                .append("</td></tr>")
                .append(
                        report.totals().totalValue() == null
                                ? ""
                                : "<tr><td>Valor total</td><td class=\"num\">"
                                        + escape(report.totals().totalValue().toPlainString())
                                        + "</td></tr>")
                .append("</table>")
                .toString();
    }

    private String titleOf(RenderableReport report) {
        return switch (report.reportType()) {
            case CONTRACT_PERIOD -> "Relatório de período";
            case CLIENT_SUMMARY -> "Resumo por cliente";
            case TIMESHEET -> "Folha de horas";
            case TICKET_DETAIL -> "Detalhamento por ticket";
            case PRODUCTIVITY -> "Relatório de produtividade";
        };
    }

    private String partialMarker(RenderableReport report) {
        return report.reopenCount() > 0 ? "PARCIAL · REABERTO" : "PARCIAL";
    }

    private String stamp(RenderableReport report) {
        return report.generatedAt() == null
                ? ""
                : STAMP.format(report.generatedAt().atZone(clock.tenantZone()));
    }

    private String line(String value) {
        return value == null || value.isBlank() ? "" : "<br/>" + escape(value);
    }

    private String cell(String value) {
        return "<td>" + escape(value) + "</td>";
    }

    private String time(java.time.Instant instant, ZoneId zone) {
        return instant == null ? "" : TIME.format(instant.atZone(zone));
    }

    /**
     * SG-06: nenhum HTML do usuário é interpretado.
     *
     * <p>Descrição de work log é texto livre e chega ao renderer sem passar por sanitização de
     * marcação em nenhum ponto anterior. Sem este escape, {@code <script>} não executaria — PDF não
     * roda script —, mas {@code </td><td>} quebraria a tabela e permitiria forjar o conteúdo de
     * outras células do documento entregue ao cliente.
     */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escape(List<String> values) {
        return values == null ? "" : escape(String.join(", ", values));
    }
}
