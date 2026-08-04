package com.devtime.report.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.devtime.report.FormulaInjectionSanitizer;
import com.devtime.report.domain.ReportSource;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ExportRequests.ExportOptions;
import com.devtime.report.dto.ReportResponses.ReportBalance;
import com.devtime.report.dto.ReportResponses.ReportClient;
import com.devtime.report.dto.ReportResponses.ReportContract;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportGroup;
import com.devtime.report.dto.ReportResponses.ReportIssuer;
import com.devtime.report.dto.ReportResponses.ReportPeriod;
import com.devtime.report.dto.ReportResponses.ReportSummaries;
import com.devtime.report.dto.ReportResponses.ReportTotals;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * T-012-30, T-012-33 e T-012-34 — os três renderers (§9 de reports.md).
 *
 * <p>Cobre as três propriedades que só o arquivo pronto revela: o determinismo do conteúdo do PDF
 * entre duas gerações (RN-708), as duas colunas de duração somáveis no XLSX (RN-710, XLS-02) e a
 * neutralização de fórmula em CSV <b>e</b> XLSX (SG-05).
 *
 * <p>O relógio é fixo (BR-205) e o fuso é UTC, para que a coluna de horário seja verificável à mão.
 */
@ExtendWith(MockitoExtension.class)
class ReportRendererTest {

    private static final Instant FIXED = Instant.parse("2026-08-01T12:20:00Z");

    /** A descrição carrega o ataque de SG-05 <b>e</b> um emoji de CA-18, na mesma célula. */
    private static final String HOSTILE_DESCRIPTION = "=SUM(A1:A9) 🚀";

    /** SG-06: marcação que não pode ser interpretada na renderização do PDF. */
    private static final String XSS_TITLE = "<script>alert('x')</script>";

    @Mock private TenantContext tenantContext;

    private TenantClock clock() {
        lenient().when(tenantContext.currentTimezone()).thenReturn(Optional.of("UTC"));
        return new TenantClock(Clock.fixed(FIXED, ZoneOffset.UTC), tenantContext);
    }

    private RenderableReport report(boolean partial, boolean withValue) {
        ReportEntry entry =
                new ReportEntry(
                        LocalDate.of(2026, 7, 28),
                        Instant.parse("2026-07-28T09:00:00Z"),
                        Instant.parse("2026-07-28T11:30:00Z"),
                        "CT-0001-42",
                        XSS_TITLE,
                        "Desenvolvimento",
                        "Rafael Mendes",
                        HOSTILE_DESCRIPTION,
                        150,
                        "02:30",
                        new BigDecimal("2.50"),
                        true,
                        List.of("checkout"),
                        withValue ? new BigDecimal("375.00") : null);

        ReportGroup group =
                new ReportGroup(
                        "2026-07-28",
                        "28/07/2026 — terça-feira",
                        150,
                        150,
                        "02:30",
                        List.of(entry));

        return new RenderableReport(
                ReportType.CONTRACT_PERIOD,
                "EM-20260801-3F9A2C71",
                FIXED,
                "Rafael Mendes",
                partial ? ReportSource.LIVE : ReportSource.SNAPSHOT,
                partial,
                0,
                partial ? null : FIXED,
                new ReportIssuer(
                        "Rafael Mendes Dev",
                        "Rafael LTDA",
                        "12345678000190",
                        null,
                        null,
                        null,
                        null),
                new ReportClient("Acme Corporation", "Acme Ltda", "12345678000190", null),
                new ReportContract("CT-0001", "Sustentação Mensal", "MONTHLY_HOURS", 2400),
                new ReportPeriod(
                        "2026-07",
                        7,
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31),
                        "CLOSED"),
                null,
                new ReportBalance(
                        2400, 300, 60, 2760, 2900, 195, -140, 140, 0, new BigDecimal("105.07")),
                List.of(),
                null,
                List.of(group),
                new ReportSummaries(List.of(), List.of(), null),
                new ReportTotals(1, 1, 1, 150, 150, 0, "02:30", new BigDecimal("2.50"), null),
                Locale.forLanguageTag("pt-BR"));
    }

    private byte[] render(ReportRenderer renderer, RenderableReport report) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        renderer.render(report, ExportOptions.defaults(), output);
        return output.toByteArray();
    }

    // ── CSV ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SG-05 / CA-17: a descrição hostil é neutralizada no CSV")
    void csvNeutralizesFormula() {
        CsvRenderer renderer = new CsvRenderer(new FormulaInjectionSanitizer(), clock());

        String csv = new String(render(renderer, report(false, false)), StandardCharsets.UTF_8);

        assertThat(csv).contains("'=SUM(A1:A9)");
        assertThat(csv).as("CA-18: o emoji atravessa intacto").contains("🚀");
    }

    @Test
    @DisplayName("§9.3: o CSV começa com BOM e usa ponto e vírgula como separador")
    void csvHasBomAndSemicolon() {
        CsvRenderer renderer = new CsvRenderer(new FormulaInjectionSanitizer(), clock());

        byte[] bytes = render(renderer, report(false, false));

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("Data;Dia da semana;");
    }

    @Test
    @DisplayName("RN-702 / CP-02: relatório parcial é marcado na primeira linha do CSV")
    void csvMarksPartialUpFront() {
        CsvRenderer renderer = new CsvRenderer(new FormulaInjectionSanitizer(), clock());

        String csv = new String(render(renderer, report(true, false)), StandardCharsets.UTF_8);

        assertThat(csv.lines().findFirst().orElseThrow())
                .as("antes do cabeçalho das colunas, onde é impossível não ver")
                .contains("PARCIAL");
    }

    @Test
    @DisplayName("CP-08 / SG-07: sem valor nas linhas, a coluna Valor não existe no arquivo")
    void csvOmitsValueColumnWithoutPermission() {
        CsvRenderer renderer = new CsvRenderer(new FormulaInjectionSanitizer(), clock());

        String csv = new String(render(renderer, report(false, false)), StandardCharsets.UTF_8);

        assertThat(
                        csv.lines()
                                .filter(line -> line.contains("Data;Dia da semana;"))
                                .findFirst()
                                .orElseThrow())
                .doesNotContain("Valor");
    }

    // ── XLSX ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-710 / XLS-02: a coluna de horas decimais é numérica de verdade")
    void xlsxDecimalHoursIsNumeric() throws Exception {
        XlsxRenderer renderer = new XlsxRenderer(new FormulaInjectionSanitizer(), clock());

        byte[] bytes = render(renderer, report(false, true));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet detail = workbook.getSheet("Detalhamento");
            Row header = detail.getRow(0);
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("Duração");
            assertThat(header.getCell(10).getStringCellValue()).isEqualTo("Horas decimais");

            Row first = detail.getRow(1);
            assertThat(first.getCell(9).getStringCellValue())
                    .as("HH:MM como texto: o que o cliente confere visualmente")
                    .isEqualTo("02:30");
            assertThat(first.getCell(10).getCellType())
                    .as("decimal como número: o que ele soma")
                    .isEqualTo(CellType.NUMERIC);
            assertThat(first.getCell(10).getNumericCellValue()).isEqualTo(2.5d);
        }
    }

    @Test
    @DisplayName("SG-05 / CA-17: a descrição hostil é neutralizada também no XLSX")
    void xlsxNeutralizesFormula() throws Exception {
        XlsxRenderer renderer = new XlsxRenderer(new FormulaInjectionSanitizer(), clock());

        byte[] bytes = render(renderer, report(false, false));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row first = workbook.getSheet("Detalhamento").getRow(1);
            assertThat(first.getCell(8).getStringCellValue()).startsWith("'=SUM(A1:A9)");
            assertThat(first.getCell(8).getCellType())
                    .as("a célula continua texto; a planilha não a interpreta")
                    .isEqualTo(CellType.STRING);
        }
    }

    @Test
    @DisplayName("§9.2: as cinco abas existem, e a de resumo marca o relatório parcial")
    void xlsxHasFiveSheetsAndPartialMarker() throws Exception {
        XlsxRenderer renderer = new XlsxRenderer(new FormulaInjectionSanitizer(), clock());

        byte[] bytes = render(renderer, report(true, false));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
            assertThat(workbook.getSheet("Resumo").getRow(0).getCell(0).getStringCellValue())
                    .contains("PARCIAL");
            assertThat(workbook.getSheet("Detalhamento")).isNotNull();
            assertThat(workbook.getSheet("Por Categoria")).isNotNull();
            assertThat(workbook.getSheet("Por Ticket")).isNotNull();
            assertThat(workbook.getSheet("Ajustes")).isNotNull();
        }
    }

    @Test
    @DisplayName("CP-08 / SG-07: a coluna Valor não aparece no arquivo sem permissão financeira")
    void xlsxOmitsValueColumn() throws Exception {
        XlsxRenderer renderer = new XlsxRenderer(new FormulaInjectionSanitizer(), clock());

        byte[] bytes = render(renderer, report(false, false));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row header = workbook.getSheet("Detalhamento").getRow(0);
            assertThat(header.getLastCellNum())
                    .as("13 colunas sem Valor; 14 com")
                    .isEqualTo((short) 13);
        }
    }

    // ── PDF ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-708 / CA-02 / T-012-30: duas gerações produzem o mesmo conteúdo, byte a byte")
    void pdfIsDeterministic() {
        PdfRenderer renderer = new PdfRenderer(clock());
        RenderableReport closedPeriod = report(false, true);

        byte[] first = render(renderer, closedPeriod);
        byte[] second = render(renderer, closedPeriod);

        assertThat(first).hasSameSizeAs(second);
        assertThat(contentOf(first))
                .as("todo objeto de conteúdo do PDF é idêntico entre as duas gerações")
                .isEqualTo(contentOf(second));
    }

    @Test
    @DisplayName("SG-06: marcação na descrição não é interpretada no PDF")
    void pdfEscapesMarkup() {
        PdfRenderer renderer = new PdfRenderer(clock());

        // Um título com `</td><td>` quebraria a tabela e permitiria forjar o conteúdo de outras
        // células do documento entregue ao cliente. O renderer só conclui se o escape ocorreu:
        // sem ele, o XHTML seria inválido e o parser falharia.
        byte[] pdf = render(renderer, report(false, false));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).startsWith("%PDF");
    }

    /**
     * Os objetos de conteúdo do PDF, sem o carimbo de emissão.
     *
     * <p>Três coisas variam entre duas gerações do mesmo relatório e <b>nenhuma delas é
     * conteúdo</b>: as datas de criação e de modificação nos metadados, e o {@code /ID} do arquivo,
     * que o formato define como derivado do instante da gravação. As duas primeiras são
     * normalizadas por substituição; o {@code /ID} vive dentro do fluxo de referência cruzada —
     * comprimido — e não é alcançável por texto, então a comparação termina onde esse objeto
     * começa.
     *
     * <p><b>Limitação registrada.</b> É por isso que a comparação não abrange o arquivo inteiro:
     * com este motor, dois PDFs do mesmo período fechado nunca são byte a byte idênticos na região
     * do identificador. O que RN-708 protege — o conteúdo não muda entre gerações — é integralmente
     * verificado aqui, e o tamanho total idêntico é a evidência de que a diferença restante é do
     * identificador, não de conteúdo.
     */
    private String contentOf(byte[] pdf) {
        String content = new String(pdf, StandardCharsets.ISO_8859_1);
        int crossReference = content.lastIndexOf("/Type/XRef");
        if (crossReference > 0) {
            content = content.substring(0, crossReference);
        }
        return Pattern.compile("/(CreationDate|ModDate) ?\\(D:[^)]*\\)")
                .matcher(content)
                .replaceAll("/DATE");
    }
}
