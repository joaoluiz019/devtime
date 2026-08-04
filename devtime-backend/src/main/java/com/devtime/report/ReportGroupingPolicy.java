package com.devtime.report;

import com.devtime.report.domain.ReportExceptions;
import com.devtime.report.domain.ReportGrouping;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportGroup;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agrupamento e ordenação do detalhamento (§6.3, §5.1 de reports.md).
 *
 * <p><b>O agrupamento é configurável; a ordenação dentro dele não é</b> (CP-05, OB-02, RS-06).
 * Permitir ordenação configurável seria trivial e desejável do ponto de vista de usabilidade, e foi
 * rejeitado porque RN-708 exige que duas gerações produzam conteúdo idêntico — e conteúdo idêntico
 * inclui a ordem das linhas. Esta classe <b>não reordena</b>: as linhas chegam na ordem normativa
 * aplicada na consulta ({@code WorkLogService.findForReport}) ou congelada no snapshot, e o
 * agrupamento apenas as distribui preservando essa ordem.
 *
 * <p>A ordem dos <b>grupos</b> segue a mesma lógica: o primeiro grupo é o da primeira linha. Com
 * {@code groupBy=DATE} isso equivale a data crescente sem nenhuma ordenação adicional, que é o que
 * §6.3 pede.
 */
@Component
@RequiredArgsConstructor
public class ReportGroupingPolicy {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DurationFormatter durationFormatter;

    /**
     * §6.3 / {@code DEVTIME-3007}: o agrupamento precisa ser suportado pelo tipo de relatório.
     *
     * @return o agrupamento efetivo, já com o default de §5.1 aplicado
     */
    public ReportGrouping validate(ReportType reportType, ReportGrouping requested) {
        ReportGrouping grouping = requested == null ? ReportGrouping.DATE : requested;
        if (!ReportGrouping.supportedBy(reportType).contains(grouping)) {
            throw ReportExceptions.groupingUnsupported(reportType, grouping);
        }
        return grouping;
    }

    /**
     * Distribui as linhas em grupos, preservando a ordem de entrada.
     *
     * @param locale usado apenas no rótulo do dia da semana (§6); a chave do grupo é sempre
     *     independente de idioma, para que dois relatórios do mesmo período em idiomas diferentes
     *     continuem comparáveis linha a linha
     */
    public List<ReportGroup> group(
            List<ReportEntry> entries, ReportGrouping grouping, Locale locale) {
        if (grouping == ReportGrouping.NONE) {
            // §5.1: lista plana. Um único grupo sem chave, para que os três renderers e a tela
            // tratem um formato só (ver ReportGroup).
            return List.of(toGroup(null, null, entries));
        }

        Map<GroupKey, List<ReportEntry>> buckets = new LinkedHashMap<>();
        for (ReportEntry entry : entries) {
            for (GroupKey key : keysOf(entry, grouping, locale)) {
                buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
            }
        }

        List<ReportGroup> groups = new ArrayList<>(buckets.size());
        buckets.forEach((key, bucket) -> groups.add(toGroup(key.key(), key.label(), bucket)));
        return List.copyOf(groups);
    }

    /**
     * Chaves de um registro no agrupamento escolhido.
     *
     * <p>Devolve uma lista porque {@code TAG} é o único agrupamento não particional: um registro
     * com três etiquetas aparece em três grupos, e a soma dos subtotais dos grupos ultrapassa o
     * total do relatório. É a única leitura possível de "agrupar por tag" — a alternativa, escolher
     * uma etiqueta por registro, precisaria de um critério de desempate que documento algum define.
     *
     * <p><b>Lacuna reportada.</b> Nem {@code business-rules.md} §13 nem §6.3 de {@code specs/012}
     * definem o que fazer com um registro <b>sem</b> etiqueta sob {@code groupBy=TAG}. Descartá-lo
     * faria os totais do detalhamento deixarem de somar as horas trabalhadas, então ele forma um
     * grupo próprio de chave nula, e o rótulo fica a cargo da camada de i18n (ART-095) em vez de
     * ser inventado aqui.
     */
    private List<GroupKey> keysOf(ReportEntry entry, ReportGrouping grouping, Locale locale) {
        return switch (grouping) {
            case DATE ->
                    List.of(
                            new GroupKey(
                                    entry.workDate().format(ISO_DATE),
                                    dateLabel(entry.workDate(), locale)));
            case WEEK -> List.of(weekKey(entry.workDate()));
            case TICKET -> List.of(new GroupKey(entry.ticketKey(), ticketLabel(entry)));
            case CATEGORY -> List.of(new GroupKey(entry.categoryName(), entry.categoryName()));
            case USER -> List.of(new GroupKey(entry.userName(), entry.userName()));
            case TAG -> tagKeys(entry);
            case NONE -> List.of(new GroupKey(null, null));
        };
    }

    private List<GroupKey> tagKeys(ReportEntry entry) {
        if (entry.tags() == null || entry.tags().isEmpty()) {
            return List.of(new GroupKey(null, null));
        }
        return entry.tags().stream().map(tag -> new GroupKey(tag, tag)).toList();
    }

    /** §6: {@code 28/07/2026 — terça-feira}. */
    private String dateLabel(LocalDate date, Locale locale) {
        return "%s — %s"
                .formatted(
                        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)),
                        date.getDayOfWeek().getDisplayName(TextStyle.FULL, locale));
    }

    /** Semana ISO, no formato {@code 2026-W31} — estável entre idiomas e ordenável como texto. */
    private GroupKey weekKey(LocalDate date) {
        String key =
                "%d-W%02d"
                        .formatted(
                                date.get(IsoFields.WEEK_BASED_YEAR),
                                date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        return new GroupKey(key, key);
    }

    /** PDF-04: chave legível e título, nunca identificador técnico. */
    private String ticketLabel(ReportEntry entry) {
        if (entry.ticketTitle() == null) {
            return entry.ticketKey();
        }
        return "%s — %s".formatted(entry.ticketKey(), entry.ticketTitle());
    }

    private ReportGroup toGroup(String key, String label, List<ReportEntry> entries) {
        int netMinutes = entries.stream().mapToInt(ReportEntry::netMinutes).sum();
        // CP-05 de reports.md: o não faturável aparece no detalhamento, marcado, mas fora do
        // subtotal faturável.
        int billableMinutes =
                entries.stream()
                        .filter(ReportEntry::billable)
                        .mapToInt(ReportEntry::netMinutes)
                        .sum();
        return new ReportGroup(
                key,
                label,
                netMinutes,
                billableMinutes,
                durationFormatter.toLabel(netMinutes),
                List.copyOf(entries));
    }

    /**
     * Chave e rótulo do grupo. A igualdade é pela chave e pelo rótulo, como todo {@code record}.
     */
    private record GroupKey(String key, String label) {}
}
