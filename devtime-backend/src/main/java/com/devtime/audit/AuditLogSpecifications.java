package com.devtime.audit;

import com.devtime.audit.domain.AuditLog;
import com.devtime.audit.dto.AuditLogRequests.AuditLogFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Predicados da consulta à trilha (BR-169: consulta dinâmica por {@code Specification}).
 *
 * <p>O filtro de tenant é explícito porque {@link AuditLog} não estende {@code TenantScopedEntity}
 * — não existe filtro automático de Hibernate a aplicar nesta tabela. O valor vem do {@code
 * TenantContext}, jamais da requisição (BR-041).
 *
 * <p>O intervalo é semi-aberto {@code [de, até)} (BR-148), e o predicado de período é sempre
 * aplicado: sem ele, o planejador varreria todas as partições mensais.
 */
final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    static Specification<AuditLog> of(
            UUID tenantId, AuditLogFilter filter, Instant from, Instant to) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            predicates.add(builder.lessThan(root.get("occurredAt"), to));

            if (hasText(filter.entityType())) {
                predicates.add(builder.equal(root.get("entityType"), filter.entityType()));
            }
            if (filter.entityId() != null) {
                predicates.add(builder.equal(root.get("entityId"), filter.entityId()));
            }
            if (filter.actorId() != null) {
                predicates.add(builder.equal(root.get("actorId"), filter.actorId()));
            }
            if (hasText(filter.action())) {
                predicates.add(builder.equal(root.get("action"), filter.action()));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
