package com.devtime.notification;

import com.devtime.notification.domain.Notification;
import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationResponses.NotificationResponse;
import com.devtime.notification.dto.NotificationResponses.NotificationTypeOption;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Conversão de {@link Notification} para DTO (BR-104 a BR-106).
 *
 * <p><b>CP-11: {@code dedupeKey} nunca é exposto.</b> É detalhe interno da deduplicação, e
 * publicá-lo convidaria à manipulação do valor cuja unicidade sustenta RN-601.
 *
 * <p>{@code emailAttempts} também fica de fora: é estado operacional da entrega, sem significado
 * para quem lê a notificação.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMapper {

    private final NotificationRouteResolver routeResolver;
    private final ObjectMapper objectMapper;

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getSeverity().name(),
                notification.getTitle(),
                notification.getBody(),
                readPayload(notification.getPayload()),
                notification.getEntityType(),
                notification.getEntityId(),
                // NT-03: a ação é derivada de entityType e entityId, não persistida — uma mudança
                // de rota no cliente não deve exigir reescrever notificações já criadas.
                routeResolver.resolve(notification),
                notification.getReadAt(),
                notification.getEmailSentAt(),
                notification.getCreatedAt());
    }

    /**
     * §9.1: catálogo exposto na tela de preferências.
     *
     * <p>Existe para que a interface liste os tipos <b>sem replicar o catálogo</b> — adicionar um
     * tipo não deve exigir alterar o frontend (§14 de notifications.md).
     */
    public List<NotificationTypeOption> availableTypes() {
        return Arrays.stream(NotificationType.values())
                .map(
                        type ->
                                new NotificationTypeOption(
                                        type.name(),
                                        type.getLabel(),
                                        type.getDefaultSeverity().name(),
                                        type.isCanMute()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException unreadable) {
            // ER-08: um payload corrompido não pode impedir a leitura da notificação — o título e
            // o corpo já bastam para entendê-la (§7).
            log.warn("payload de notificação ilegível notificationId={}", "omitido");
            return Map.of();
        }
    }
}
