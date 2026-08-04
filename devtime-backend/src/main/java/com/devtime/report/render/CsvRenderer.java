package com.devtime.report.render;

import com.devtime.report.FormulaInjectionSanitizer;
import com.devtime.report.domain.ExportFormat;
import com.devtime.report.dto.ExportRequests.ExportOptions;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportGroup;
import com.devtime.shared.time.TenantClock;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CSV do detalhamento (§9.3 de reports.md).
 *
 * <p>Escreve linha a linha no fluxo: nenhuma estrutura intermediária guarda o arquivo inteiro
 * (CP-13).
 *
 * <p><b>UTF-8 com BOM</b> e separador {@code ;} no locale {@code pt-BR}. As duas escolhas existem
 * pelo mesmo motivo prático: sem o BOM, o Excel em português abre o arquivo em ANSI e destrói a
 * acentuação; com {@code ,} como separador, ele coloca a linha inteira na primeira coluna, porque
 * no locale brasileiro a vírgula é separador decimal.
 */
@Component
@RequiredArgsConstructor
public class CsvRenderer implements ReportRenderer {

    /** {@code EF BB BF} em UTF-8 — o marcador que o Excel procura para decidir a codificação. */
    private static final String BYTE_ORDER_MARK = "﻿";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final FormulaInjectionSanitizer sanitizer;
    private final TenantClock clock;

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public void render(RenderableReport report, ExportOptions options, OutputStream output) {
        boolean includeValue = report.hasFinancialColumn();
        // RN-009 / ART-031: os horários saem no fuso do tenant, nunca no do servidor. Um relatório
        // gerado numa instância em UTC mostraria 12:00 para uma sessão iniciada às 09:00.
        ZoneId zone = clock.tenantZone();

        // O writer não é fechado: fechá-lo fecharia o OutputStream de quem chamou (ver
        // ReportRenderer#render). O flush é o que garante que tudo saiu.
        Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        try {
            writer.write(BYTE_ORDER_MARK);
            writePartialMarker(writer, report);
            writeRow(writer, ReportColumns.headers(includeValue));

            for (ReportGroup group : report.groups()) {
                for (ReportEntry entry : group.entries()) {
                    writeRow(writer, cells(entry, includeValue, zone, report));
                }
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao escrever o CSV do relatório", e);
        }
    }

    /**
     * RN-702 e CP-02: a marcação PARCIAL também no CSV.
     *
     * <p>Um CSV não tem cabeçalho de página onde pôr uma marca d'água, então ela vai como primeira
     * linha do arquivo — antes do cabeçalho das colunas, onde é impossível não ver ao abrir. Um
     * relatório parcial que circule sem essa indicação será tratado como final (CP-03).
     */
    private void writePartialMarker(Writer writer, RenderableReport report) throws IOException {
        if (!report.isPartial()) {
            return;
        }
        String marker =
                report.reopenCount() > 0
                        ? "PARCIAL — período reaberto; os números ainda podem mudar"
                        : "PARCIAL — período em aberto; os números ainda podem mudar";
        writeRow(writer, List.of(marker));
    }

    private List<String> cells(
            ReportEntry entry, boolean includeValue, ZoneId zone, RenderableReport report) {
        List<String> cells =
                new java.util.ArrayList<>(
                        List.of(
                                text(entry.workDate()),
                                dayOfWeek(entry, report),
                                time(entry.startedAt(), zone),
                                time(entry.endedAt(), zone),
                                text(entry.ticketKey()),
                                text(entry.ticketTitle()),
                                text(entry.categoryName()),
                                text(entry.userName()),
                                text(entry.description()),
                                text(entry.durationLabel()),
                                // §9.3: numérico sem símbolo. O ponto decimal é preservado; a
                                // conversão para vírgula é do leitor, pelo locale dele.
                                text(entry.decimalHours()),
                                entry.billable() ? "Sim" : "Não",
                                entry.tags() == null ? "" : String.join(", ", entry.tags())));
        if (includeValue) {
            cells.add(text(entry.value()));
        }
        return cells;
    }

    private String dayOfWeek(ReportEntry entry, RenderableReport report) {
        if (entry.workDate() == null) {
            return "";
        }
        return entry.workDate()
                .getDayOfWeek()
                .getDisplayName(java.time.format.TextStyle.FULL, report.locale());
    }

    private String time(java.time.Instant instant, ZoneId zone) {
        return instant == null ? "" : TIME.format(instant.atZone(zone));
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Uma linha, com escape e sanitização.
     *
     * <p>SG-05 é aplicada <b>antes</b> do escape de CSV: sanitizar depois de envolver a célula em
     * aspas colocaria a aspa simples fora do campo, onde a planilha a ignoraria e a fórmula
     * voltaria a executar.
     */
    private void writeRow(Writer writer, List<String> cells) throws IOException {
        StringBuilder row = new StringBuilder();
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                row.append(';');
            }
            row.append(escape(sanitizer.sanitize(cells.get(index))));
        }
        // CRLF: é o fim de linha que o RFC 4180 define e o que o Excel espera. O `lineEndings` do
        // Spotless vale para o código-fonte, não para o artefato entregue ao cliente.
        row.append("\r\n");
        writer.write(row.toString());
    }

    private String escape(String cell) {
        if (cell == null) {
            return "";
        }
        if (cell.indexOf(';') < 0
                && cell.indexOf('"') < 0
                && cell.indexOf('\n') < 0
                && cell.indexOf('\r') < 0) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }
}
