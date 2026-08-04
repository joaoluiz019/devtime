package com.devtime.report;

import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Uma linha do detalhamento, a partir de qualquer das duas fontes (§24).
 *
 * <p>BR-106: a formatação de duração e a de valor acontecem aqui, nunca na entidade e nunca na
 * tela. É o que garante que PDF, XLSX, CSV e interface exibam exatamente o mesmo texto para o mesmo
 * minuto — condição para que o cliente possa comparar o arquivo com a tela linha a linha.
 *
 * <p>CP-08 e SG-07: quando {@code hourlyRate} chega nulo — por ausência de {@code
 * CONTRACT_VIEW_FINANCIAL} ou por contrato sem valor hora —, {@code value} sai nulo em <b>todas</b>
 * as saídas, inclusive no arquivo. A omissão acontece aqui, e não em cada renderer, porque três
 * omissões independentes seriam três oportunidades de esquecer uma.
 */
@Component
@RequiredArgsConstructor
public class ReportEntryMapper {

    private final DurationFormatter durationFormatter;
    private final MoneyFormatter moneyFormatter;

    /** Caminho ao vivo: a projeção que {@code WorkLogService.findForReport} devolve. */
    public ReportEntry fromWorkLog(
            com.devtime.worklog.dto.WorkLogReportViews.ReportEntry source, BigDecimal hourlyRate) {
        return build(
                source.workDate(),
                source.startedAt(),
                source.endedAt(),
                source.ticketKey(),
                source.ticketTitle(),
                source.categoryName(),
                source.userName(),
                source.description(),
                source.netMinutes(),
                source.billableMinutes(),
                source.billable(),
                source.tags(),
                hourlyRate);
    }

    /**
     * Caminho do snapshot: um item do array {@code workLogs} do payload congelado.
     *
     * <p>Campos ausentes são tolerados e viram nulo. Um snapshot gravado na versão 1 do payload não
     * tem {@code ticketTitle}, {@code startedAt}, {@code userName} nem {@code tags}, e ele
     * <b>não</b> é migrado (o snapshot é imutável por definição, RP-03). Devolver nulo é a
     * informação honesta: o dado não foi congelado no fechamento e não pode ser reconstruído sem
     * falsificá-lo — buscá-lo na tabela atual é exatamente o que RN-701 proíbe.
     */
    public ReportEntry fromSnapshot(JsonNode node, BigDecimal hourlyRate) {
        int netMinutes = node.path("netMinutes").asInt();
        return build(
                localDate(node, "workDate"),
                instant(node, "startedAt"),
                instant(node, "endedAt"),
                text(node, "ticketKey"),
                text(node, "ticketTitle"),
                text(node, "categoryName"),
                text(node, "userName"),
                text(node, "description"),
                netMinutes,
                node.path("billableMinutes").asInt(),
                node.path("billable").asBoolean(false),
                tags(node),
                hourlyRate);
    }

    private ReportEntry build(
            LocalDate workDate,
            Instant startedAt,
            Instant endedAt,
            String ticketKey,
            String ticketTitle,
            String categoryName,
            String userName,
            String description,
            int netMinutes,
            int billableMinutes,
            boolean billable,
            List<String> tags,
            BigDecimal hourlyRate) {
        return new ReportEntry(
                workDate,
                startedAt,
                endedAt,
                ticketKey,
                ticketTitle,
                categoryName,
                userName,
                description,
                netMinutes,
                durationFormatter.toLabel(netMinutes),
                durationFormatter.toDecimalHours(netMinutes),
                billable,
                tags == null ? List.of() : List.copyOf(tags),
                // CP-05: a linha não faturável aparece com valor zero, não omitida. Omitir o valor
                // dela seria indistinguível da omissão por falta de permissão (CP-08), e as duas
                // situações exigem leituras opostas de quem confere o documento.
                moneyFormatter.valueOf(billableMinutes, hourlyRate));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private LocalDate localDate(JsonNode node, String field) {
        String raw = text(node, field);
        return raw == null ? null : LocalDate.parse(raw);
    }

    private Instant instant(JsonNode node, String field) {
        String raw = text(node, field);
        return raw == null ? null : Instant.parse(raw);
    }

    private List<String> tags(JsonNode node) {
        JsonNode array = node.get("tags");
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>(array.size());
        array.forEach(tag -> tags.add(tag.asText()));
        return List.copyOf(tags);
    }
}
