package com.devtime.report;

import com.devtime.report.domain.ReportGrouping;
import com.devtime.report.domain.ReportSource;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.report.dto.ReportResponses.ProductivityByUser;
import com.devtime.report.dto.ReportResponses.ProductivityByWeek;
import com.devtime.report.dto.ReportResponses.ProductivityReportResponse;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportRange;
import com.devtime.shared.time.TenantClock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Relatório de produtividade (§7.4 de reports.md).
 *
 * <p><b>Nunca compara membros entre si nem produz ranking</b> (IDG-02 de {@code personas.md},
 * CA-13). A restrição é estrutural, não uma nota de rodapé: as linhas por usuário saem em ordem
 * <b>alfabética</b>, não por métrica. Ordenar por horas seria um ranking com outro nome, e a ordem
 * é a primeira coisa que quem lê interpreta como classificação.
 *
 * <p>Pelo mesmo motivo não existem posição, medalha, destaque de maior e menor, nem média do time
 * para comparação individual. Os números são absolutos por pessoa.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductivityAssembler {

    private static final int RATE_SCALE = 2;

    private final ReportHeaderBuilder headerBuilder;
    private final ReportEntryLoader entryLoader;
    private final ReportSummaryBuilder summaryBuilder;
    private final DurationFormatter durationFormatter;
    private final TenantClock clock;

    public ProductivityReportResponse assemble(
            ReportFilters filters, ReportGrouping grouping, UUID restrictToUserId) {
        List<ReportEntry> entries =
                entryLoader.load(
                        entryLoader.filterFor(
                                null,
                                null,
                                null,
                                null,
                                filters.from(),
                                filters.to(),
                                filters,
                                restrictToUserId),
                        // §7.4 é sobre tempo, não sobre dinheiro: nenhuma métrica de produtividade
                        // depende de valor, e cruzar contratos tornaria a taxa arbitrária.
                        null);

        int workDays = workDaysBetween(filters.from(), filters.to());

        log.info(
                "relatório gerado tipo={} fonte=LIVE de={} ate={} linhas={}",
                ReportType.PRODUCTIVITY,
                filters.from(),
                filters.to(),
                entries.size());

        return new ProductivityReportResponse(
                ReportType.PRODUCTIVITY,
                clock.now(),
                headerBuilder.currentUser(),
                headerBuilder.issueId(null),
                ReportSource.LIVE,
                true,
                grouping,
                headerBuilder.issuer(),
                new ReportRange(filters.from(), filters.to()),
                workDays,
                byUser(entries, workDays),
                byWeek(entries),
                summaryBuilder.summaries(entries, entryLoader.includeByUser()),
                summaryBuilder.totals(entries));
    }

    /** Valores absolutos por pessoa, em ordem alfabética (IDG-02). */
    private List<ProductivityByUser> byUser(List<ReportEntry> entries, int workDays) {
        Map<String, int[]> totals = new LinkedHashMap<>();
        for (ReportEntry entry : entries) {
            int[] accumulator = totals.computeIfAbsent(entry.userName(), ignored -> new int[2]);
            accumulator[0] += entry.netMinutes();
            if (entry.billable()) {
                accumulator[1] += entry.netMinutes();
            }
        }

        List<ProductivityByUser> rows = new ArrayList<>(totals.size());
        totals.forEach(
                (userName, accumulator) ->
                        rows.add(
                                new ProductivityByUser(
                                        userName,
                                        accumulator[0],
                                        accumulator[1],
                                        durationFormatter.toLabel(accumulator[0]),
                                        durationFormatter.toDecimalHours(accumulator[0]),
                                        rate(accumulator[1], accumulator[0]),
                                        perWorkDay(accumulator[0], workDays))));
        rows.sort(
                Comparator.comparing(
                        ProductivityByUser::userName, Comparator.nullsLast(String::compareTo)));
        return List.copyOf(rows);
    }

    /** Semanas ISO do intervalo, na ordem cronológica em que aparecem nas linhas. */
    private List<ProductivityByWeek> byWeek(List<ReportEntry> entries) {
        Map<String, int[]> totals = new LinkedHashMap<>();
        Map<String, LocalDate> anyDayOfWeek = new LinkedHashMap<>();

        for (ReportEntry entry : entries) {
            String isoWeek = isoWeek(entry.workDate());
            int[] accumulator = totals.computeIfAbsent(isoWeek, ignored -> new int[2]);
            accumulator[0] += entry.netMinutes();
            if (entry.billable()) {
                accumulator[1] += entry.netMinutes();
            }
            anyDayOfWeek.putIfAbsent(isoWeek, entry.workDate());
        }

        List<ProductivityByWeek> rows = new ArrayList<>(totals.size());
        totals.forEach(
                (isoWeek, accumulator) -> {
                    LocalDate reference = anyDayOfWeek.get(isoWeek);
                    LocalDate weekStart = reference.with(DayOfWeek.MONDAY);
                    rows.add(
                            new ProductivityByWeek(
                                    isoWeek,
                                    weekStart,
                                    weekStart.plusDays(6),
                                    accumulator[0],
                                    accumulator[1],
                                    durationFormatter.toLabel(accumulator[0])));
                });
        rows.sort(Comparator.comparing(ProductivityByWeek::isoWeek));
        return List.copyOf(rows);
    }

    private String isoWeek(LocalDate date) {
        return "%d-W%02d"
                .formatted(
                        date.get(IsoFields.WEEK_BASED_YEAR),
                        date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Taxa de faturabilidade em pontos percentuais, 2 casas. */
    private BigDecimal rate(int part, int total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Minutos por dia útil do intervalo.
     *
     * <p>Dia útil é de segunda a sexta, a mesma definição que {@code ProjectionCalculator} de
     * {@code 010-dashboard} usa. Feriados não entram: o produto não tem calendário de feriados, e
     * assumir um país produziria um número errado para quem trabalha em outro.
     */
    private BigDecimal perWorkDay(int minutes, int workDays) {
        if (workDays == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(workDays), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private int workDaysBetween(LocalDate from, LocalDate to) {
        int workDays = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY
                    && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workDays++;
            }
        }
        return workDays;
    }
}
