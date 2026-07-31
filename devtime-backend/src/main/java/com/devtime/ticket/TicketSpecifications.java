package com.devtime.ticket;

import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.domain.TicketType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros compostos da listagem de tickets (tickets.md §6).
 *
 * <p>BR-169 / SG-10: toda condição é montada por {@code Specification} tipada, com parâmetros
 * vinculados. Concatenar o termo de busca em SQL abriria injeção no exato campo que o usuário
 * controla (RP-04).
 *
 * <p>O filtro de tenant <b>não</b> aparece aqui: ele é aplicado automaticamente pelo
 * {@code @Filter} de Hibernate (ART-022, BR-046). Escrevê-lo manualmente esconderia os casos em que
 * o filtro não está ativo.
 */
public final class TicketSpecifications {

    private TicketSpecifications() {}

    public static Specification<Ticket> withFilters(
            UUID contractId,
            Collection<UUID> contractIds,
            Collection<TicketStatus> statuses,
            Collection<TicketType> types,
            Collection<TicketPriority> priorities,
            UUID assigneeId,
            UUID reporterId,
            Collection<UUID> ticketIdsWithTags,
            String search,
            Boolean overEstimate) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (contractId != null) {
                predicates.add(builder.equal(root.get("contractId"), contractId));
            }
            if (contractIds != null) {
                // Cliente sem contratos não possui tickets: um IN vazio precisa resultar em
                // "nenhum", não em "todos".
                predicates.add(
                        contractIds.isEmpty()
                                ? builder.disjunction()
                                : root.get("contractId").in(contractIds));
            }
            if (isPresent(statuses)) {
                predicates.add(root.get("status").in(statuses));
            }
            if (isPresent(types)) {
                predicates.add(root.get("type").in(types));
            }
            if (isPresent(priorities)) {
                predicates.add(root.get("priority").in(priorities));
            }
            if (assigneeId != null) {
                predicates.add(builder.equal(root.get("assigneeId"), assigneeId));
            }
            if (reporterId != null) {
                predicates.add(builder.equal(root.get("reporterId"), reporterId));
            }
            if (ticketIdsWithTags != null) {
                // Conjunção de etiquetas resolvida antes, em consulta própria: um IN vazio precisa
                // resultar em "nenhum ticket", não em "todos".
                predicates.add(
                        ticketIdsWithTags.isEmpty()
                                ? builder.disjunction()
                                : root.get("id").in(ticketIdsWithTags));
            }
            if (search != null && !search.isBlank()) {
                // Busca sem diferenciar caixa, sobre título e descrição. A busca sem acento
                // exigiria a extensão `unaccent`, que não consta das instaladas em V001;
                // acrescentá-la é decisão de infraestrutura e não desta feature (IA-02).
                String pattern = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
                predicates.add(
                        builder.or(
                                builder.like(builder.lower(root.get("title")), pattern),
                                builder.like(
                                        builder.lower(
                                                builder.coalesce(root.get("description"), "")),
                                        pattern)));
            }
            if (Boolean.TRUE.equals(overEstimate)) {
                // RN-309: sem estimativa não há estouro a comparar (CX-09).
                predicates.add(builder.isNotNull(root.get("estimatedMinutes")));
                predicates.add(
                        builder.greaterThan(
                                root.get("spentMinutes"), root.get("estimatedMinutes")));
            }
            if (Boolean.FALSE.equals(overEstimate)) {
                predicates.add(
                        builder.or(
                                builder.isNull(root.get("estimatedMinutes")),
                                builder.lessThanOrEqualTo(
                                        root.get("spentMinutes"), root.get("estimatedMinutes"))));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean isPresent(Collection<?> values) {
        return values != null && !values.isEmpty();
    }
}
