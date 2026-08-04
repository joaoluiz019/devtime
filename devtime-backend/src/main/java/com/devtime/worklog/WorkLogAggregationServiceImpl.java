package com.devtime.worklog;

import com.devtime.category.CategoryService;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import com.devtime.tag.TagLinkService;
import com.devtime.worklog.domain.WorkLog;
import com.devtime.worklog.dto.WorkLogFilter;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCalendarDay;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCalendarResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCategoryTotal;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogGroupTotal;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogRangeTotals;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogTotalsResponse;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calendário e totais (ver {@link WorkLogAggregationService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkLogAggregationServiceImpl implements WorkLogAggregationService {

    private final WorkLogRepository repository;
    private final WorkLogOwnershipPolicy ownershipPolicy;
    private final CategoryService categoryService;
    private final TagLinkService tagLinkService;

    @Override
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_VIEW_ANY') or hasPermission(null, 'WORKLOG_VIEW_OWN')")
    public WorkLogCalendarResponse calendar(LocalDate from, LocalDate to, UUID userId) {
        // IMP-02: o escopo de MEMBER vence o filtro pedido. Um MEMBER que informasse o userId de
        // um colega receberia o próprio calendário, nunca o dele.
        UUID scoped = ownershipPolicy.dataScopeUserId().orElse(userId);

        List<WorkLogCalendarDay> days =
                repository.aggregateByDay(from, to, scoped).stream()
                        .map(
                                row ->
                                        new WorkLogCalendarDay(
                                                row.getDay(),
                                                (int) row.getTotalMinutes(),
                                                (int) row.getBillableMinutes(),
                                                (int) row.getEntryCount()))
                        .toList();

        return new WorkLogCalendarResponse(
                from, to, days, days.stream().mapToInt(WorkLogCalendarDay::totalMinutes).sum());
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_VIEW_ANY') or hasPermission(null, 'WORKLOG_VIEW_OWN')")
    public WorkLogTotalsResponse totals(WorkLogFilter filter) {
        List<UUID> idsWithTags =
                filter.tagIds() == null || filter.tagIds().isEmpty()
                        ? null
                        : tagLinkService.workLogIdsWithAllTags(filter.tagIds());

        // SG-03: os totais usam exatamente a mesma Specification da listagem, escopo incluído.
        // Um total calculado sobre um conjunto maior revelaria, pela diferença, horas de colegas.
        List<WorkLog> matching =
                repository.findAll(
                        WorkLogSpecifications.withFilters(
                                filter,
                                ownershipPolicy.dataScopeUserId().orElse(null),
                                idsWithTags));

        int totalMinutes = matching.stream().mapToInt(WorkLog::getNetMinutes).sum();
        int billableMinutes = matching.stream().mapToInt(WorkLog::billableMinutes).sum();

        Map<UUID, String> categoryNames =
                categoryService.list(null, null).stream()
                        .collect(Collectors.toMap(CategoryResponse::id, CategoryResponse::name));

        List<WorkLogCategoryTotal> byCategory =
                matching.stream()
                        .collect(Collectors.groupingBy(WorkLog::getCategoryId))
                        .entrySet()
                        .stream()
                        .map(
                                entry ->
                                        new WorkLogCategoryTotal(
                                                entry.getKey(),
                                                categoryNames.get(entry.getKey()),
                                                entry.getValue().stream()
                                                        .mapToInt(WorkLog::getNetMinutes)
                                                        .sum(),
                                                entry.getValue().size()))
                        .sorted(
                                Comparator.comparingInt(WorkLogCategoryTotal::totalMinutes)
                                        .reversed()
                                        .thenComparing(
                                                WorkLogCategoryTotal::categoryName,
                                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();

        return new WorkLogTotalsResponse(
                totalMinutes,
                billableMinutes,
                totalMinutes - billableMinutes,
                matching.size(),
                byCategory);
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_VIEW_ANY') or hasPermission(null, 'WORKLOG_VIEW_OWN')")
    public WorkLogRangeTotals totalsInRange(LocalDate from, LocalDate to) {
        // SG-02 de specs/010: o escopo entra na consulta. Um MEMBER que recebesse o total do tenant
        // descobriria as horas dos colegas pela subtração das próprias.
        var aggregate =
                repository.sumInRange(from, to, ownershipPolicy.dataScopeUserId().orElse(null));
        if (aggregate == null) {
            return WorkLogRangeTotals.empty();
        }
        return new WorkLogRangeTotals(
                (int) aggregate.getNetMinutes(), (int) aggregate.getBillableMinutes());
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_VIEW_ANY') or hasPermission(null, 'WORKLOG_VIEW_OWN')")
    public List<WorkLogGroupTotal> minutesByClient(LocalDate from, LocalDate to) {
        return toGroupTotals(
                repository.aggregateByClientInRange(
                        from, to, ownershipPolicy.dataScopeUserId().orElse(null)));
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_VIEW_ANY') or hasPermission(null, 'WORKLOG_VIEW_OWN')")
    public List<WorkLogGroupTotal> minutesByCategory(LocalDate from, LocalDate to) {
        return toGroupTotals(
                repository.aggregateByCategoryInRange(
                        from, to, ownershipPolicy.dataScopeUserId().orElse(null)));
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_VIEW_ANY') or hasPermission(null, 'WORKLOG_VIEW_OWN')")
    public List<WorkLogGroupTotal> minutesByContract(LocalDate from, LocalDate to) {
        return toGroupTotals(
                repository.aggregateByContractInRange(
                        from, to, ownershipPolicy.dataScopeUserId().orElse(null)));
    }

    /** Ordenação decrescente por minutos: a maior fatia primeiro, como os gráficos a exibem. */
    private List<WorkLogGroupTotal> toGroupTotals(
            List<WorkLogRepository.GroupAggregate> aggregates) {
        return aggregates.stream()
                .map(
                        row ->
                                new WorkLogGroupTotal(
                                        row.getGroupId(),
                                        (int) row.getNetMinutes(),
                                        (int) row.getBillableMinutes()))
                .sorted(Comparator.comparingInt(WorkLogGroupTotal::netMinutes).reversed())
                .toList();
    }
}
