package com.devtime.notification;

import com.devtime.notification.domain.Notification;
import com.devtime.notification.domain.NotificationExceptions;
import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationRequests.NotificationFilter;
import com.devtime.notification.dto.NotificationRequests.NotificationPreferencesRequest;
import com.devtime.notification.dto.NotificationResponses.MarkAllReadResponse;
import com.devtime.notification.dto.NotificationResponses.NotificationPreferencesResponse;
import com.devtime.notification.dto.NotificationResponses.NotificationReadResponse;
import com.devtime.notification.dto.NotificationResponses.NotificationResponse;
import com.devtime.notification.dto.NotificationResponses.UnreadCountResponse;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.pagination.PageRequestFactory;
import com.devtime.shared.pagination.PageResponse;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.user.UserAccountService;
import com.devtime.user.dto.UserAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Central de notificações (ver {@link NotificationQueryService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private static final String KEY_EMAIL_ENABLED = "emailNotifications";
    private static final String KEY_MUTED = "mutedNotificationTypes";

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final UserAccountService userAccountService;
    private final PageRequestFactory pageRequestFactory;
    private final ObjectMapper objectMapper;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'NOTIFICATION_VIEW')")
    public PageResponse<NotificationResponse> search(NotificationFilter filter, Pageable pageable) {
        Pageable validated = pageRequestFactory.validate(pageable); // RN-012
        return PageResponse.of(
                repository.findAll(
                        NotificationSpecifications.forRecipient(currentUserId(), filter),
                        validated),
                mapper::toResponse);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'NOTIFICATION_VIEW')")
    public UnreadCountResponse unreadCount() {
        UUID recipientId = currentUserId();
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        repository
                .countUnreadBySeverity(recipientId)
                .forEach(row -> bySeverity.put(row.getSeverity().name(), row.getTotal()));
        return new UnreadCountResponse(repository.countUnread(recipientId), bySeverity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'NOTIFICATION_VIEW')")
    public NotificationReadResponse markRead(UUID id) {
        Notification notification = require(id);
        // §8.1: idempotente. Remarcar não altera readAt — o instante em que o usuário viu pela
        // primeira vez é a informação com valor.
        if (!notification.isRead()) {
            notification.setReadAt(clock.now());
        }
        return new NotificationReadResponse(
                notification.getId(),
                notification.getReadAt(),
                repository.countUnread(notification.getRecipientId()));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'NOTIFICATION_VIEW')")
    public NotificationReadResponse markUnread(UUID id) {
        Notification notification = require(id);
        notification.setReadAt(null); // FA-13
        return new NotificationReadResponse(
                notification.getId(), null, repository.countUnread(notification.getRecipientId()));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'NOTIFICATION_VIEW')")
    public MarkAllReadResponse markAllRead() {
        UUID recipientId = currentUserId();
        int marked = repository.markAllRead(recipientId, clock.now());
        return new MarkAllReadResponse(marked, repository.countUnread(recipientId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'NOTIFICATION_VIEW')")
    public void delete(UUID id) {
        Notification notification = require(id);
        repository.softDelete(notification.getId(), clock.now(), currentUserId()); // RN-003
    }

    @Override
    @PreAuthorize("hasPermission(null, 'NOTIFICATION_VIEW')")
    public NotificationPreferencesResponse preferences() {
        return toPreferences(readPreferences(currentAccount().preferences()));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'NOTIFICATION_VIEW')")
    public NotificationPreferencesResponse updatePreferences(
            NotificationPreferencesRequest request) {
        List<String> muted = validateMutedTypes(request.mutedNotificationTypes());

        userAccountService.updateNotificationPreferences(
                currentUserId(), request.emailNotifications(), muted);

        // FA-16: efeito imediato; notificações já criadas não são afetadas, e um e-mail já enviado
        // não é revertido (CX-12).
        Map<String, Object> updated = readPreferences(currentAccount().preferences());
        if (request.emailNotifications() != null) {
            updated.put(KEY_EMAIL_ENABLED, request.emailNotifications());
        }
        if (muted != null) {
            updated.put(KEY_MUTED, muted);
        }
        return toPreferences(updated);
    }

    /**
     * §9.2: todo tipo informado precisa existir e ser silenciável.
     *
     * <p>A verificação de {@code canMute} acontece na <b>escrita</b>, e não apenas no envio:
     * recusar aqui deixa claro ao usuário que aquele alerta não pode ser desligado, em vez de
     * aceitar a preferência e ignorá-la silenciosamente depois.
     */
    private List<String> validateMutedTypes(List<String> rawTypes) {
        if (rawTypes == null) {
            return null;
        }
        return rawTypes.stream()
                .map(
                        raw -> {
                            NotificationType type =
                                    NotificationType.byName(raw)
                                            .orElseThrow(
                                                    () -> NotificationExceptions.unknownType(raw));
                            if (!type.isCanMute()) {
                                throw NotificationExceptions.typeCannotBeMuted(type); // 4001
                            }
                            return type.name();
                        })
                .distinct()
                .toList();
    }

    /**
     * CE-P-04 / SG-05: notificação de outro destinatário responde {@code 404}, nunca {@code 403}.
     *
     * <p>Distinguir revelaria a existência da notificação alheia — e a existência já é informação
     * sobre o que aconteceu com outra pessoa.
     */
    private Notification require(UUID id) {
        return repository
                .findByIdAndRecipient(id, currentUserId())
                .orElseThrow(() -> EntityNotFoundException.of(Notification.class, id));
    }

    private NotificationPreferencesResponse toPreferences(Map<String, Object> preferences) {
        Object emailEnabled = preferences.get(KEY_EMAIL_ENABLED);
        List<String> muted =
                preferences.get(KEY_MUTED) instanceof List<?> values
                        ? values.stream().map(String::valueOf).toList()
                        : List.of();
        return new NotificationPreferencesResponse(
                // entities.md §6.2.1: o padrão é receber.
                !(emailEnabled instanceof Boolean enabled) || enabled,
                muted,
                mapper.availableTypes());
    }

    private UserAccount currentAccount() {
        return userAccountService.require(currentUserId());
    }

    private UUID currentUserId() {
        return tenantContext.requireUserId();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPreferences(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(json, Map.class));
        } catch (JsonProcessingException unreadable) {
            log.warn("preferências ilegíveis; exibindo os padrões de entities.md §6.2.1");
            return new LinkedHashMap<>();
        }
    }
}
