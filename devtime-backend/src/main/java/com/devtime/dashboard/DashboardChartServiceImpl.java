package com.devtime.dashboard;

import com.devtime.category.CategoryService;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import com.devtime.client.ClientService;
import com.devtime.client.dto.ClientResponses.ClientRef;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.ContractResponses.ContractDashboardCard;
import com.devtime.dashboard.domain.ChartType;
import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.dashboard.domain.DashboardScope;
import com.devtime.dashboard.dto.DashboardResponses.ChartPointDto;
import com.devtime.dashboard.dto.DashboardResponses.ChartResponse;
import com.devtime.dashboard.dto.DashboardResponses.ChartSliceDto;
import com.devtime.dashboard.dto.DashboardResponses.DashboardChartsDto;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.DateRange;
import com.devtime.worklog.WorkLogAggregationService;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCalendarDay;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogGroupTotal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gráficos com cache (ver {@link DashboardChartService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardChartServiceImpl implements DashboardChartService {

    /** Rótulo das fatias de {@code billable-ratio}; nomes de negócio, não identificadores. */
    private static final String LABEL_BILLABLE = "Faturável";

    private static final String LABEL_NON_BILLABLE = "Não faturável";

    /** Cores dos dois estados de faturamento; não há entidade de origem de onde derivá-las. */
    private static final String COLOR_BILLABLE = "#10B981";

    private static final String COLOR_NON_BILLABLE = "#94A3B8";

    private final WorkLogAggregationService aggregationService;
    private final ClientService clientService;
    private final CategoryService categoryService;
    private final ContractService contractService;
    private final DashboardPeriodResolver periodResolver;
    private final DashboardScopeResolver scopeResolver;
    private final ChartGapFiller gapFiller;
    private final PercentageNormalizer percentageNormalizer;
    private final DashboardChartCache cache;
    private final TenantContext tenantContext;

    @Override
    @PreAuthorize(
            "hasPermission(null, 'DASHBOARD_VIEW_ANY') or hasPermission(null,"
                    + " 'DASHBOARD_VIEW_OWN')")
    public ChartResponse chart(
            String type, DashboardPeriodType period, LocalDate from, LocalDate to) {
        return chart(ChartType.fromExternalName(type), periodResolver.resolve(period, from, to));
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'DASHBOARD_VIEW_ANY') or hasPermission(null,"
                    + " 'DASHBOARD_VIEW_OWN')")
    public ChartResponse chart(ChartType type, DateRange range) {
        return cache.get(cacheKey(type, range), () -> build(type, range));
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'DASHBOARD_VIEW_ANY') or hasPermission(null,"
                    + " 'DASHBOARD_VIEW_OWN')")
    public DashboardChartsDto mainCharts(DateRange range) {
        return new DashboardChartsDto(
                chart(ChartType.DAILY_MINUTES, range).points(),
                chart(ChartType.BY_CLIENT, range).slices(),
                chart(ChartType.BY_CATEGORY, range).slices());
    }

    private DashboardChartCache.CacheKey cacheKey(ChartType type, DateRange range) {
        DashboardScope scope = scopeResolver.resolve();
        return new DashboardChartCache.CacheKey(
                tenantContext.requireTenantId(),
                scope,
                // SG-07: no escopo USER a chave carrega o usuário; sem isso dois membros do mesmo
                // tenant compartilhariam o gráfico um do outro.
                scope == DashboardScope.USER ? tenantContext.requireUserId() : null,
                type,
                periodResolver.cacheKeyOf(range));
    }

    private ChartResponse build(ChartType type, DateRange range) {
        return switch (type) {
            case DAILY_MINUTES ->
                    ChartResponse.ofPoints(type.getExternalName(), dailySeries(range));
            case CONSUMPTION_TREND ->
                    ChartResponse.ofPoints(type.getExternalName(), consumptionTrend(range));
            case BY_CLIENT -> ChartResponse.ofSlices(type.getExternalName(), byClient(range));
            case BY_CATEGORY -> ChartResponse.ofSlices(type.getExternalName(), byCategory(range));
            case BY_CONTRACT -> ChartResponse.ofSlices(type.getExternalName(), byContract(range));
            case BILLABLE_RATIO ->
                    ChartResponse.ofSlices(type.getExternalName(), billableRatio(range));
        };
    }

    /** CP-04: 30 pontos, com os dias sem registro visíveis em zero. */
    private List<ChartPointDto> dailySeries(DateRange range) {
        LocalDate firstDay = gapFiller.firstDayOfSeries(range.end());
        List<WorkLogCalendarDay> aggregated =
                aggregationService.calendar(firstDay, range.end(), null).days();
        return gapFiller.fill(aggregated, range.end());
    }

    /**
     * Consumo faturável acumulado dia a dia.
     *
     * <p><b>Lacuna de documentação:</b> ver {@link ChartType#CONSUMPTION_TREND}. A base é a mesma
     * série diária, acumulada — o que torna a curva monótona e legível como "quanto do saldo já foi
     * consumido até aqui".
     */
    private List<ChartPointDto> consumptionTrend(DateRange range) {
        List<ChartPointDto> daily = dailySeries(range);
        List<ChartPointDto> accumulated = new ArrayList<>(daily.size());
        int net = 0;
        int billable = 0;
        for (ChartPointDto point : daily) {
            net += point.netMinutes();
            billable += point.billableMinutes();
            accumulated.add(new ChartPointDto(point.date(), net, billable));
        }
        return List.copyOf(accumulated);
    }

    private List<ChartSliceDto> byClient(DateRange range) {
        List<WorkLogGroupTotal> totals =
                aggregationService.minutesByClient(range.start(), range.end());
        Map<UUID, ClientRef> clients =
                clientService
                        .findRefs(totals.stream().map(WorkLogGroupTotal::groupId).toList())
                        .stream()
                        .collect(Collectors.toMap(ClientRef::id, Function.identity()));

        return percentageNormalizer.normalize(
                totals.stream()
                        .map(
                                total -> {
                                    ClientRef client = clients.get(total.groupId());
                                    return new ChartSliceDto(
                                            total.groupId(),
                                            client == null ? null : client.name(),
                                            client == null ? null : client.color(),
                                            total.netMinutes(),
                                            null);
                                })
                        .toList());
    }

    /**
     * Distribuição por categoria.
     *
     * <p>CX-16: o rótulo vem de {@code CategoryService.getAllForReport}, que inclui categorias
     * inativas e excluídas. RN-505 migra os registros na exclusão, mas a migração não alcança um
     * catálogo editado antes de {@code 008} existir — e a fatia continua sendo preservada com
     * rótulo nulo em vez de descartada, porque descartá-la faria os percentuais deixarem de somar
     * os minutos realmente trabalhados.
     */
    private List<ChartSliceDto> byCategory(DateRange range) {
        List<WorkLogGroupTotal> totals =
                aggregationService.minutesByCategory(range.start(), range.end());
        Map<UUID, CategoryResponse> categories =
                categoryService.getAllForReport().stream()
                        .collect(Collectors.toMap(CategoryResponse::id, Function.identity()));

        return percentageNormalizer.normalize(
                totals.stream()
                        .map(
                                total -> {
                                    CategoryResponse category = categories.get(total.groupId());
                                    return new ChartSliceDto(
                                            total.groupId(),
                                            category == null ? null : category.name(),
                                            category == null ? null : category.color(),
                                            total.netMinutes(),
                                            null);
                                })
                        .toList());
    }

    /** SG-05: só entram contratos visíveis ao papel; os demais nem são rotulados. */
    private List<ChartSliceDto> byContract(DateRange range) {
        List<WorkLogGroupTotal> totals =
                aggregationService.minutesByContract(range.start(), range.end());
        Map<UUID, ContractDashboardCard> visible =
                contractService
                        .findActiveForDashboard(scopeResolver.resolve() == DashboardScope.USER)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        ContractDashboardCard::contractId,
                                        Function.identity(),
                                        (first, second) -> first,
                                        LinkedHashMap::new));

        return percentageNormalizer.normalize(
                totals.stream()
                        .filter(total -> visible.containsKey(total.groupId()))
                        .map(
                                total -> {
                                    ContractDashboardCard card = visible.get(total.groupId());
                                    return new ChartSliceDto(
                                            total.groupId(),
                                            card.code() + " — " + card.name(),
                                            null,
                                            total.netMinutes(),
                                            null);
                                })
                        .toList());
    }

    /** Faturável × não faturável no intervalo (RN-223: não faturável é visível, fora do saldo). */
    private List<ChartSliceDto> billableRatio(DateRange range) {
        var totals = aggregationService.totalsInRange(range.start(), range.end());
        int nonBillable = totals.netMinutes() - totals.billableMinutes();
        return percentageNormalizer.normalize(
                List.of(
                        new ChartSliceDto(
                                null,
                                LABEL_BILLABLE,
                                COLOR_BILLABLE,
                                totals.billableMinutes(),
                                null),
                        new ChartSliceDto(
                                null, LABEL_NON_BILLABLE, COLOR_NON_BILLABLE, nonBillable, null)));
    }
}
