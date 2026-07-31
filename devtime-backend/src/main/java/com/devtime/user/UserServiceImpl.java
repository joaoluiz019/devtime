package com.devtime.user;

import com.devtime.user.domain.User;
import com.devtime.user.dto.UserSummary;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura de resumos de exibição (ver {@link UserService}).
 *
 * <p>Sem {@code @PreAuthorize}: BR-065 exige a declaração em serviços de <b>escrita</b>, e esta
 * classe não escreve. O acesso é indireto — quem chama já verificou {@code TICKET_VIEW} ou {@code
 * COMMENT_VIEW} — e o conteúdo é o mesmo que {@code MEMBER_VIEW} concede a todos os papéis (§7 de
 * {@code permissions.md}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository repository;

    @Override
    public Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds) {
        Set<UUID> distinct =
                userIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return repository.findAllByIdIn(distinct).stream()
                .collect(Collectors.toMap(User::getId, this::toSummary));
    }

    @Override
    public UserSummary summaryOf(UUID userId) {
        if (userId == null) {
            return null;
        }
        // RN-458: um usuário excluído logicamente some de findById; o vínculo histórico permanece
        // e é exibido como "Usuário Removido" em vez de produzir 404 em um ticket íntegro.
        return repository
                .findById(userId)
                .map(this::toSummary)
                .orElseGet(() -> UserSummary.removed(userId));
    }

    @Override
    public List<UserSummary> findByHandles(Collection<String> handles) {
        Set<String> normalized =
                handles.stream()
                        .filter(handle -> handle != null && !handle.isBlank())
                        .map(handle -> handle.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
        if (normalized.isEmpty()) {
            return List.of();
        }
        return repository.findAllByDisplayNameIn(normalized).stream().map(this::toSummary).toList();
    }

    private UserSummary toSummary(User user) {
        String name = user.getDisplayName() == null ? user.getFullName() : user.getDisplayName();
        String handle =
                user.getDisplayName() == null
                        ? null
                        : user.getDisplayName().toLowerCase(Locale.ROOT);
        return new UserSummary(user.getId(), name, handle, user.getAvatarUrl());
    }
}
