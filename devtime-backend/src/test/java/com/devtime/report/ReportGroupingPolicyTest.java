package com.devtime.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.report.domain.ReportExceptions;
import com.devtime.report.domain.ReportGrouping;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportGroup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Agrupamento e ordenação do detalhamento (§6.3, §5.1, CP-05).
 *
 * <p>A propriedade central verificada aqui é <b>negativa</b>: o agrupamento não reordena nada. A
 * ordem normativa vem da consulta, e se esta classe a alterasse — mesmo aplicando o mesmo critério
 * — duas gerações do mesmo PDF poderiam divergir, e RN-708 seria inverificável.
 */
class ReportGroupingPolicyTest {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ReportGroupingPolicy policy = new ReportGroupingPolicy(new DurationFormatter());

    private ReportEntry entry(
            LocalDate workDate, String ticketKey, String category, int minutes, boolean billable) {
        return new ReportEntry(
                workDate,
                null,
                null,
                ticketKey,
                ticketKey + " — título",
                category,
                "Rafael Mendes",
                "descrição",
                minutes,
                "01:00",
                new BigDecimal("1.00"),
                billable,
                List.of(),
                null);
    }

    @Test
    @DisplayName("§5.1: DATE é o default quando o pedido não informa agrupamento")
    void dateIsDefault() {
        assertThat(policy.validate(ReportType.CONTRACT_PERIOD, null))
                .isEqualTo(ReportGrouping.DATE);
    }

    @Test
    @DisplayName("§6.3 / DEVTIME-3007: WEEK não é suportado pelo relatório de período")
    void unsupportedGroupingIsRejected() {
        assertThatThrownBy(() -> policy.validate(ReportType.CONTRACT_PERIOD, ReportGrouping.WEEK))
                .isInstanceOf(ReportExceptions.GroupingUnsupportedException.class);
    }

    @Test
    @DisplayName("§6.3: TICKET_DETAIL não agrupa por ticket — produziria um grupo só")
    void ticketDetailRejectsTicketGrouping() {
        assertThatThrownBy(() -> policy.validate(ReportType.TICKET_DETAIL, ReportGrouping.TICKET))
                .isInstanceOf(ReportExceptions.GroupingUnsupportedException.class);
    }

    @Test
    @DisplayName("§5.1: NONE devolve um único grupo sem chave — a lista plana")
    void noneProducesFlatList() {
        List<ReportEntry> entries =
                List.of(
                        entry(LocalDate.of(2026, 7, 28), "CT-0001-1", "Desenvolvimento", 60, true),
                        entry(LocalDate.of(2026, 7, 29), "CT-0001-2", "Suporte", 30, false));

        List<ReportGroup> groups = policy.group(entries, ReportGrouping.NONE, PT_BR);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).key()).isNull();
        assertThat(groups.get(0).entries()).hasSize(2);
    }

    @Test
    @DisplayName("CP-05: o agrupamento preserva a ordem de entrada, dentro e entre os grupos")
    void orderIsPreserved() {
        ReportEntry first = entry(LocalDate.of(2026, 7, 28), "CT-0001-9", "A", 60, true);
        ReportEntry second = entry(LocalDate.of(2026, 7, 28), "CT-0001-1", "A", 30, true);
        ReportEntry third = entry(LocalDate.of(2026, 7, 27), "CT-0001-5", "A", 15, true);

        List<ReportGroup> groups =
                policy.group(List.of(first, second, third), ReportGrouping.DATE, PT_BR);

        // O primeiro grupo é o da primeira linha, mesmo que a data dele seja posterior: reordenar
        // por data aqui esconderia um defeito de ordenação na consulta em vez de expô-lo.
        assertThat(groups.get(0).key()).isEqualTo("2026-07-28");
        assertThat(groups.get(0).entries()).containsExactly(first, second);
        assertThat(groups.get(1).key()).isEqualTo("2026-07-27");
    }

    @Test
    @DisplayName("CP-05 de reports.md: o subtotal faturável exclui o não faturável")
    void billableSubtotalExcludesNonBillable() {
        List<ReportEntry> entries =
                List.of(
                        entry(LocalDate.of(2026, 7, 28), "CT-0001-1", "A", 60, true),
                        entry(LocalDate.of(2026, 7, 28), "CT-0001-2", "A", 30, false));

        ReportGroup group = policy.group(entries, ReportGrouping.DATE, PT_BR).get(0);

        assertThat(group.totalNetMinutes()).isEqualTo(90);
        assertThat(group.totalBillableMinutes()).isEqualTo(60);
        assertThat(group.durationLabel()).isEqualTo("01:30");
    }

    @Test
    @DisplayName("§6: o rótulo de data traz o dia da semana no idioma do tenant")
    void dateLabelCarriesDayOfWeek() {
        ReportGroup group =
                policy.group(
                                List.of(
                                        entry(
                                                LocalDate.of(2026, 7, 28),
                                                "CT-0001-1",
                                                "A",
                                                60,
                                                true)),
                                ReportGrouping.DATE,
                                PT_BR)
                        .get(0);

        assertThat(group.key()).as("a chave é estável entre idiomas").isEqualTo("2026-07-28");
        assertThat(group.label()).startsWith("28/07/2026 — ");
    }

    @Test
    @DisplayName("A chave da semana ISO é estável e ordenável como texto")
    void weekKeyIsIsoAndSortable() {
        ReportGroup group =
                policy.group(
                                List.of(
                                        entry(
                                                LocalDate.of(2026, 1, 5),
                                                "CT-0001-1",
                                                "A",
                                                60,
                                                true)),
                                ReportGrouping.WEEK,
                                PT_BR)
                        .get(0);

        assertThat(group.key()).isEqualTo("2026-W02");
    }
}
