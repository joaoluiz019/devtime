package com.devtime.report.render;

import com.devtime.report.FormulaInjectionSanitizer;
import com.devtime.report.domain.ExportFormat;
import com.devtime.report.dto.ExportRequests.ExportOptions;
import com.devtime.report.dto.ReportResponses.ReportAdjustment;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportGroup;
import com.devtime.report.dto.ReportResponses.ReportSummarySlice;
import com.devtime.shared.time.TenantClock;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * XLSX do relatório (§9.2 de reports.md).
 *
 * <p><b>SXSSF, não XSSF</b> (CP-13, OB-06). O modelo em fluxo mantém em memória apenas uma janela
 * de linhas e descarrega o resto em disco temporário; o modelo completo constrói a planilha inteira
 * na heap, e 50.000 linhas ali derrubam a instância. A diferença não é de desempenho — é entre
 * concluir e falhar.
 *
 * <p><b>Duas colunas de duração</b> (RN-710, CP-06, OB-03): {@code HH:MM} como texto, que o cliente
 * confere visualmente, e horas decimais como <b>número de verdade</b> (XLS-02), que ele soma. Uma
 * coluna só forçaria a escolha, e o cliente resolveria manualmente na calculadora.
 */
@Component
@RequiredArgsConstructor
public class XlsxRenderer implements ReportRenderer {

    /** Linhas mantidas em memória pelo SXSSF; o restante é descarregado (CP-13). */
    private static final int ROW_WINDOW = 200;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** §9.2 nomeia as cinco abas. */
    private static final String SHEET_SUMMARY = "Resumo";

    private static final String SHEET_DETAIL = "Detalhamento";
    private static final String SHEET_BY_CATEGORY = "Por Categoria";
    private static final String SHEET_BY_TICKET = "Por Ticket";
    private static final String SHEET_ADJUSTMENTS = "Ajustes";

    private final FormulaInjectionSanitizer sanitizer;
    private final TenantClock clock;

    @Override
    public ExportFormat format() {
        return ExportFormat.XLSX;
    }

    @Override
    public void render(RenderableReport report, ExportOptions options, OutputStream output) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW)) {
            // O temporário do SXSSF é comprimido: com 50.000 linhas a diferença em disco é de
            // dezenas de megabytes, e o worker pode estar processando mais de uma exportação.
            workbook.setCompressTempFiles(true);

            Styles styles = new Styles(workbook);
            writeSummarySheet(workbook, styles, report);
            writeDetailSheet(workbook, styles, report);
            writeSliceSheet(
                    workbook,
                    styles,
                    SHEET_BY_CATEGORY,
                    report.summaries() == null ? List.of() : report.summaries().byCategory());
            writeSliceSheet(
                    workbook,
                    styles,
                    SHEET_BY_TICKET,
                    report.summaries() == null ? List.of() : report.summaries().byTicket());
            writeAdjustmentsSheet(workbook, styles, report);

            workbook.write(output);
            // Remove os arquivos temporários do fluxo; sem isto eles sobrevivem ao processo e o
            // disco do worker cresce a cada exportação.
            workbook.dispose();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao escrever o XLSX do relatório", e);
        }
    }

    /** §9.2: emissor, cliente, contrato, período e todas as linhas do saldo. */
    private void writeSummarySheet(SXSSFWorkbook workbook, Styles styles, RenderableReport report) {
        Sheet sheet = workbook.createSheet(SHEET_SUMMARY);
        RowWriter rows = new RowWriter(sheet, styles);

        if (report.isPartial()) {
            // CP-02 e CP-03: proeminente, na primeira linha, não em rodapé.
            rows.title(partialMarker(report));
        }
        rows.title("Relatório " + report.reportType().name());
        rows.pair("Emissão", report.issueId());
        rows.pair("Gerado em", text(report.generatedAt()));
        rows.pair("Gerado por", report.generatedByName());
        rows.pair("Fonte", report.source().name());
        rows.blank();

        if (report.issuer() != null) {
            rows.title("Emissor");
            rows.pair("Nome", report.issuer().name());
            rows.pair("Razão social", report.issuer().legalName());
            rows.pair("Documento", report.issuer().documentNumber());
            rows.blank();
        }
        if (report.client() != null) {
            rows.title("Cliente");
            rows.pair("Nome", report.client().name());
            rows.pair("Razão social", report.client().legalName());
            rows.pair("Documento", report.client().documentNumber());
            rows.blank();
        }
        if (report.contract() != null) {
            rows.title("Contrato");
            rows.pair("Código", report.contract().code());
            rows.pair("Nome", report.contract().name());
            rows.blank();
        }
        if (report.period() != null) {
            rows.title("Período");
            rows.pair("Rótulo", report.period().label());
            rows.pair("Início", text(report.period().startDate()));
            rows.pair("Fim", text(report.period().endDate()));
            rows.pair("Situação", report.period().status());
            rows.blank();
        }
        if (report.balance() != null) {
            rows.title("Saldo");
            rows.pair("Contratado", report.balance().contractedMinutes());
            rows.pair("Transportado", report.balance().carriedInMinutes());
            rows.pair("Ajustes", report.balance().adjustmentMinutes());
            rows.pair("Disponível", report.balance().availableMinutes());
            rows.pair("Consumido", report.balance().consumedMinutes());
            rows.pair("Não faturável", report.balance().nonBillableMinutes());
            rows.pair("Restante", report.balance().remainingMinutes());
            rows.pair("Excedente", report.balance().overageMinutes());
        }
        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 12000);
    }

    /** §9.2: uma linha por registro de horas, com as 13 ou 14 colunas. */
    private void writeDetailSheet(SXSSFWorkbook workbook, Styles styles, RenderableReport report) {
        Sheet sheet = workbook.createSheet(SHEET_DETAIL);
        boolean includeValue = report.hasFinancialColumn();
        List<String> headers = ReportColumns.headers(includeValue);
        ZoneId zone = clock.tenantZone();

        Row header = sheet.createRow(0);
        for (int column = 0; column < headers.size(); column++) {
            header.createCell(column).setCellValue(headers.get(column));
            header.getCell(column).setCellStyle(styles.header());
            sheet.setColumnWidth(column, widthOf(column));
        }

        int rowIndex = 1;
        for (ReportGroup group : report.groups()) {
            for (ReportEntry entry : group.entries()) {
                writeEntry(sheet.createRow(rowIndex++), entry, includeValue, zone, report, styles);
            }
        }

        // XLS-01: primeira linha congelada e com filtro automático.
        sheet.createFreezePane(0, 1);
        if (rowIndex > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, headers.size() - 1));
        }
        writeSubtotalRow(sheet, styles, rowIndex, includeValue);
    }

    private void writeEntry(
            Row row,
            ReportEntry entry,
            boolean includeValue,
            ZoneId zone,
            RenderableReport report,
            Styles styles) {
        int column = 0;
        row.createCell(column++).setCellValue(text(entry.workDate()));
        row.createCell(column++)
                .setCellValue(
                        entry.workDate() == null
                                ? ""
                                : entry.workDate()
                                        .getDayOfWeek()
                                        .getDisplayName(TextStyle.FULL, report.locale()));
        row.createCell(column++).setCellValue(time(entry.startedAt(), zone));
        row.createCell(column++).setCellValue(time(entry.endedAt(), zone));
        row.createCell(column++).setCellValue(sanitize(entry.ticketKey()));
        row.createCell(column++).setCellValue(sanitize(entry.ticketTitle()));
        row.createCell(column++).setCellValue(sanitize(entry.categoryName()));
        row.createCell(column++).setCellValue(sanitize(entry.userName()));
        row.createCell(column++).setCellValue(sanitize(entry.description()));
        row.createCell(column++).setCellValue(text(entry.durationLabel()));

        // XLS-02 / RN-710: número de verdade, não texto. É esta célula que o cliente soma.
        var decimalCell = row.createCell(column++);
        decimalCell.setCellValue(
                entry.decimalHours() == null ? 0d : entry.decimalHours().doubleValue());
        decimalCell.setCellStyle(styles.decimal());

        row.createCell(column++).setCellValue(entry.billable() ? "Sim" : "Não");
        row.createCell(column++)
                .setCellValue(entry.tags() == null ? "" : String.join(", ", entry.tags()));
        if (includeValue) {
            var valueCell = row.createCell(column);
            valueCell.setCellValue(entry.value() == null ? 0d : entry.value().doubleValue());
            valueCell.setCellStyle(styles.money());
        }
    }

    /**
     * XLS-03: o total usa {@code SUBTOTAL}, respondendo aos filtros que o usuário aplicar.
     *
     * <p>Um {@code SUM} fixo mostraria o total de tudo mesmo com a planilha filtrada — e quem
     * filtrou por categoria quer o total daquela categoria, não o do arquivo.
     */
    private void writeSubtotalRow(
            Sheet sheet, Styles styles, int firstFreeRow, boolean includeValue) {
        if (firstFreeRow <= 1) {
            return;
        }
        Row totals = sheet.createRow(firstFreeRow + 1);
        totals.createCell(0).setCellValue("Total");
        totals.getCell(0).setCellStyle(styles.header());

        int decimalColumn = ReportColumns.DECIMAL_HOURS_INDEX;
        var decimalCell = totals.createCell(decimalColumn);
        decimalCell.setCellFormula(subtotal(decimalColumn, firstFreeRow));
        decimalCell.setCellStyle(styles.decimal());

        if (includeValue) {
            int valueColumn = ReportColumns.BASE.size();
            var valueCell = totals.createCell(valueColumn);
            valueCell.setCellFormula(subtotal(valueColumn, firstFreeRow));
            valueCell.setCellStyle(styles.money());
        }
    }

    /** {@code 109} é {@code SUM} ignorando linhas ocultas por filtro. */
    private String subtotal(int column, int lastRowExclusive) {
        String reference = columnLetter(column);
        return "SUBTOTAL(109,%s2:%s%d)".formatted(reference, reference, lastRowExclusive);
    }

    private String columnLetter(int column) {
        StringBuilder letter = new StringBuilder();
        int value = column;
        while (value >= 0) {
            letter.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        }
        return letter.toString();
    }

    private void writeSliceSheet(
            SXSSFWorkbook workbook, Styles styles, String name, List<ReportSummarySlice> slices) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Rótulo");
        header.createCell(1).setCellValue("Minutos");
        header.createCell(2).setCellValue("Percentual");
        header.getCell(0).setCellStyle(styles.header());
        header.getCell(1).setCellStyle(styles.header());
        header.getCell(2).setCellStyle(styles.header());

        int rowIndex = 1;
        for (ReportSummarySlice slice : slices) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(sanitize(slice.label()));
            row.createCell(1).setCellValue(slice.minutes());
            var percentage = row.createCell(2);
            percentage.setCellValue(value(slice.percentage()));
            percentage.setCellStyle(styles.decimal());
        }
        sheet.setColumnWidth(0, 10000);
        sheet.createFreezePane(0, 1);
    }

    /** §9.2: os ajustes com a justificativa (CP-06). */
    private void writeAdjustmentsSheet(
            SXSSFWorkbook workbook, Styles styles, RenderableReport report) {
        Sheet sheet = workbook.createSheet(SHEET_ADJUSTMENTS);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Minutos");
        header.createCell(1).setCellValue("Motivo");
        header.createCell(2).setCellValue("Justificativa");
        header.createCell(3).setCellValue("Aplicado em");
        for (int column = 0; column <= 3; column++) {
            header.getCell(column).setCellStyle(styles.header());
        }

        int rowIndex = 1;
        for (ReportAdjustment adjustment : report.adjustments()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(adjustment.minutes());
            row.createCell(1).setCellValue(sanitize(adjustment.reason()));
            row.createCell(2).setCellValue(sanitize(adjustment.justification()));
            row.createCell(3).setCellValue(text(adjustment.appliedAt()));
        }
        sheet.setColumnWidth(2, 16000);
        sheet.createFreezePane(0, 1);
    }

    private String partialMarker(RenderableReport report) {
        return report.reopenCount() > 0
                ? "PARCIAL — período reaberto; os números ainda podem mudar"
                : "PARCIAL — período em aberto; os números ainda podem mudar";
    }

    /** XLS-04: larguras aproximadas por coluna. */
    private int widthOf(int column) {
        return switch (column) {
            case 5, 8 -> 16000;
            case 4, 6, 7, 12 -> 8000;
            default -> 4000;
        };
    }

    private String sanitize(String cell) {
        String sanitized = sanitizer.sanitize(cell);
        return sanitized == null ? "" : sanitized;
    }

    private String time(java.time.Instant instant, ZoneId zone) {
        return instant == null ? "" : TIME.format(instant.atZone(zone));
    }

    private double value(BigDecimal number) {
        return number == null ? 0d : number.doubleValue();
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Estilos criados uma vez por planilha.
     *
     * <p>POI limita o número de estilos por arquivo, e criar um por célula estoura o limite muito
     * antes de 50.000 linhas — é um dos modos de falha específicos da escrita em fluxo.
     */
    private static final class Styles {

        private final CellStyle header;
        private final CellStyle decimal;
        private final CellStyle money;

        private Styles(SXSSFWorkbook workbook) {
            CreationHelper helper = workbook.getCreationHelper();

            Font bold = workbook.createFont();
            bold.setBold(true);
            this.header = workbook.createCellStyle();
            this.header.setFont(bold);

            this.decimal = workbook.createCellStyle();
            this.decimal.setDataFormat(helper.createDataFormat().getFormat("0.00"));

            this.money = workbook.createCellStyle();
            this.money.setDataFormat(helper.createDataFormat().getFormat("#,##0.00"));
        }

        CellStyle header() {
            return header;
        }

        CellStyle decimal() {
            return decimal;
        }

        CellStyle money() {
            return money;
        }
    }

    /** Escrita sequencial de pares rótulo/valor na aba de resumo. */
    private static final class RowWriter {

        private final Sheet sheet;
        private final Styles styles;
        private int rowIndex;

        private RowWriter(Sheet sheet, Styles styles) {
            this.sheet = sheet;
            this.styles = styles;
        }

        void title(String text) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(text);
            row.getCell(0).setCellStyle(styles.header());
        }

        void pair(String label, Object value) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(label);
            row.createCell(1).setCellValue(value == null ? "" : value.toString());
        }

        void blank() {
            rowIndex++;
        }
    }
}
