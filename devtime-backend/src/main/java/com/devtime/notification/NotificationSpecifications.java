package com.devtime.notification;

import com.devtime.notification.domain.Notification;
import com.devtime.notification.domain.NotificationSeverity;
import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationRequests.NotificationFilter;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros da central (§7 de notifications.md).
 *
 * <p>BR-169: {@link Specification} tipada, nunca concatenação de SQL.
 *
 * <p>O {@code recipientId} é aplicado <b>primeiro e sempre</b>, e não como filtro opcional: ele é o
 * escopo de segurança (§16, SG-01), e nenhum parâmetro da requisição pode ampliá-lo.
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {}

    public static Specification<Notification> forRecipient(
            UUID recipientId, NotificationFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            // SG-01: escopo de segurança, não filtro. Sempre presente.
            predicates.add(builder.equal(root.get("recipientId"), recipientId));

            if (filter.read() != null) {
                predicates.add(
                        filter.read()
                                ? builder.isNotNull(root.get("readAt"))
                                : builder.isNull(root.get("readAt")));
            }
            if (filter.type() != null && !filter.type().isBlank()) {
                NotificationType.byName(filter.type())
                        .ifPresent(type -> predicates.add(builder.equal(root.get("type"), type)));
            }
            if (filter.severity() != null && !filter.severity().isBlank()) {
                predicates.add(
                        builder.equal(
                                root.get("severity"),
                                NotificationSeverity.valueOf(filter.severity().toUpperCase())));
            }
            if (filter.createdFrom() != null) {
                predicates.add(
                        builder.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
            }
            if (filter.createdTo() != null) {
                predicates.add(
                        builder.lessThanOrEqualTo(root.get("createdAt"), filter.createdTo()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
