package com.devtime.worklog;

import com.devtime.worklog.domain.WorkLog;
import com.devtime.worklog.domain.WorkLogSource;
import com.devtime.worklog.dto.WorkLogFilter;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros de listagem e de totais (worklogs.md §5 e §8).
 *
 * <p>BR-169: consulta dinâmica por {@link Specification} tipada, nunca por concatenação de SQL
 * (SG-11). IMP-02 / CP-16: <b>todo</b> filtro — inclusive o escopo de dados de {@code MEMBER} — é
 * aplicado na consulta. Filtrar em memória vazaria a existência de registros de colegas pela
 * contagem e pela paginação, mesmo sem exibir o conteúdo (SG-03).
 */
public final class WorkLogSpecifications {

    private WorkLogSpecifications() {}

    /**
     * @param scopeUserId §9 de permissions.md — quando presente, restringe a consulta a este
     *     usuário. É o escopo de {@code MEMBER}, e é aplicado <b>antes</b> dos filtros do
     *     requisitante para que nenhum deles consiga ampliá-lo.
     * @param workLogIdsWithTags identificadores resolvidos pela conjunção de etiquetas; lista vazia
     *     significa "nenhum registro", nunca "sem filtro"
     */
    public static Specification<WorkLog> withFilters(
            WorkLogFilter filter, UUID scopeUserId, List<UUID> workLogIdsWithTags) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (scopeUserId != null) {
                predicates.add(builder.equal(root.get("userId"), scopeUserId));
            }
            if (filter.userId() != null) {
                predicates.add(builder.equal(root.get("userId"), filter.userId()));
            }
            if (filter.ticketId() != null) {
                predicates.add(builder.equal(root.get("ticketId"), filter.ticketId()));
            }
            if (filter.contractId() != null) {
                predicates.add(builder.equal(root.get("contractId"), filter.contractId()));
            }
            if (filter.clientId() != null) {
                predicates.add(builder.equal(root.get("clientId"), filter.clientId()));
            }
            if (filter.categoryId() != null) {
                predicates.add(builder.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (filter.dateFrom() != null) {
                predicates.add(
                        builder.greaterThanOrEqualTo(root.get("workDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("workDate"), filter.dateTo()));
            }
            if (filter.billable() != null) {
                predicates.add(builder.equal(root.get("billable"), filter.billable()));
            }
            if (filter.source() != null && !filter.source().isBlank()) {
                predicates.add(
                        builder.equal(
                                root.get("source"),
                                WorkLogSource.valueOf(filter.source().toUpperCase())));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                predicates.add(
                        builder.like(
                                builder.lower(root.get("description")),
                                "%" + filter.search().toLowerCase() + "%"));
            }
            if (workLogIdsWithTags != null) {
                // Lista vazia significa "nenhum registro possui todas as etiquetas pedidas".
                // Traduzir isso para "sem filtro" devolveria a listagem inteira — o oposto do que
                // o usuário pediu.
                predicates.add(
                        workLogIdsWithTags.isEmpty()
                                ? builder.disjunction()
                                : root.get("id").in(workLogIdsWithTags));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
