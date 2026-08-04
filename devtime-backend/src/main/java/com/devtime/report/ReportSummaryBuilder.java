package com.devtime.report;

import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportSummaries;
import com.devtime.report.dto.ReportResponses.ReportSummarySlice;
import com.devtime.report.dto.ReportResponses.ReportTotals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resumos de distribuição e totais consolidados (§6, blocos {@code summaries} e {@code totals}).
 *
 * <p>Calculados sobre as <b>linhas do relatório</b>, e não por consulta agregada ao banco. É uma
 * decisão, não conveniência: o relatório já materializou exatamente o conjunto que vai imprimir, e
 * uma segunda agregação com os mesmos filtros poderia divergir dele — bastaria um registro criado
 * entre as duas consultas para que o total do rodapé não somasse as linhas acima dele. Em período
 * fechado a divergência seria pior ainda, porque a segunda consulta leria a tabela e não o
 * snapshot.
 */
@Component
@RequiredArgsConstructor
public class ReportSummaryBuilder {

    private static final int PERCENTAGE_SCALE = 2;

    private final DurationFormatter durationFormatter;

    /**
     * §6, {@code summaries}.
     *
     * @param includeByUser CP-04: falso para {@code MEMBER}, que nunca vê a distribuição do time
     */
    public ReportSummaries summaries(List<ReportEntry> entries, boolean includeByUser) {
        int total = entries.stream().mapToInt(ReportEntry::netMinutes).sum();
        return new ReportSummaries(
                slices(entries, ReportEntry::categoryName, total),
                slices(entries, ReportEntry::ticketKey, total),
                includeByUser ? slices(entries, ReportEntry::userName, total) : null);
    }

    /**
     * §6, {@code totals}.
     *
     * <p>{@code distinctDays} e {@code distinctTickets} são contagens do conjunto impresso, não do
     * intervalo: um relatório de 31 dias com trabalho em 21 deles informa 21, que é a resposta à
     * pergunta que o cliente faz ao ver o documento.
     */
    public ReportTotals totals(List<ReportEntry> entries) {
        int netMinutes = entries.stream().mapToInt(ReportEntry::netMinutes).sum();
        int billableMinutes =
                entries.stream()
                        .filter(ReportEntry::billable)
                        .mapToInt(ReportEntry::netMinutes)
                        .sum();

        return new ReportTotals(
                entries.size(),
                (int) entries.stream().map(ReportEntry::workDate).distinct().count(),
                (int) entries.stream().map(ReportEntry::ticketKey).distinct().count(),
                netMinutes,
                billableMinutes,
                netMinutes - billableMinutes,
                durationFormatter.toLabel(netMinutes),
                durationFormatter.toDecimalHours(netMinutes),
                totalValue(entries));
    }

    /**
     * Soma dos valores das linhas.
     *
     * <p>Nulo quando <b>nenhuma</b> linha tem valor — o bloco monetário foi omitido (CP-03, CP-08)
     * — e não zero: zero afirmaria que o período não vale nada, o que é diferente de "não é
     * possível dizer quanto vale".
     */
    private BigDecimal totalValue(List<ReportEntry> entries) {
        List<BigDecimal> values =
                entries.stream().map(ReportEntry::value).filter(v -> v != null).toList();
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Fatias por rótulo, da maior para a menor.
     *
     * <p>Ordenar por minutos é apresentação, não ordenação de linhas: as linhas do detalhamento
     * seguem a ordem normativa de §6.3 e não são tocadas aqui. O resumo responde "onde foi o
     * tempo", e a resposta começa pela maior fatia.
     *
     * <p>Rótulo nulo é preservado como fatia própria: descartá-lo faria a soma das fatias deixar de
     * bater com o total, e o leitor não teria como saber que faltou algo.
     */
    private List<ReportSummarySlice> slices(
            List<ReportEntry> entries, Function<ReportEntry, String> label, int total) {
        Map<String, Integer> byLabel = new LinkedHashMap<>();
        for (ReportEntry entry : entries) {
            byLabel.merge(label.apply(entry), entry.netMinutes(), Integer::sum);
        }

        List<ReportSummarySlice> slices = new ArrayList<>(byLabel.size());
        byLabel.forEach(
                (key, minutes) ->
                        slices.add(
                                new ReportSummarySlice(
                                        key,
                                        key,
                                        // A cor pertence a cliente e categoria, e o relatório
                                        // agrupa por rótulo, não por identificador — resolver a
                                        // cor exigiria a chave, que §6 não traz nas fatias.
                                        null,
                                        minutes,
                                        percentage(minutes, total))));
        slices.sort((a, b) -> Integer.compare(b.minutes(), a.minutes()));
        return List.copyOf(slices);
    }

    /** 2 casas, {@code HALF_UP} (§6.4). Total zero devolve zero, nunca divisão por zero. */
    private BigDecimal percentage(int minutes, int total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(PERCENTAGE_SCALE);
        }
        return BigDecimal.valueOf(minutes)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }
}
