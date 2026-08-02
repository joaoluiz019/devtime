package com.devtime.user;

import com.devtime.audit.AuditActorNameResolver;
import com.devtime.user.dto.UserSummary;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementação de {@link AuditActorNameResolver} (spec 002 §24, {@code AuditLogMapper}).
 *
 * <p>Reusa {@link UserService#findSummaries} em vez de consultar diretamente: o resumo já resolve o
 * nome de exibição preferido e é o mesmo texto que aparece ao lado de tickets e comentários — a
 * trilha não pode chamar a mesma pessoa por um nome diferente.
 */
@Component
@RequiredArgsConstructor
public class UserAuditActorNameResolver implements AuditActorNameResolver {

    private final UserService userService;

    @Override
    public Map<UUID, String> namesOf(Collection<UUID> actorIds) {
        if (actorIds == null || actorIds.isEmpty()) {
            return Map.of();
        }
        return userService.findSummaries(actorIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> nameOf(entry.getValue())));
    }

    private String nameOf(UserSummary summary) {
        return summary == null ? UserSummary.REMOVED_USER_NAME : summary.name();
    }
}
