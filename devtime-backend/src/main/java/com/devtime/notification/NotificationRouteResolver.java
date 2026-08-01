package com.devtime.notification;

import com.devtime.notification.domain.Notification;
import com.devtime.notification.dto.NotificationResponses.NotificationAction;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolução do link de navegação da notificação (NT-03, §7).
 *
 * <p>NT-03: <b>toda notificação leva a uma ação concreta</b>. Uma notificação sem destino é ruído —
 * ela informa que algo aconteceu e deixa o usuário procurar onde.
 *
 * <p>A rota é do <b>frontend</b>, não da API (§7): quem clica quer chegar à tela, e a rota da API
 * não é navegável. Ela é derivada de {@code entityType} e {@code entityId}, e não persistida, para
 * que uma mudança de rota no cliente não exija reescrever notificações já criadas.
 */
@Component
public class NotificationRouteResolver {

    /** Destino quando a origem não é navegável — a própria central. */
    private static final String FALLBACK_ROUTE = "/notifications";

    public NotificationAction resolve(Notification notification) {
        String route = routeOf(notification.getEntityType(), notification.getEntityId());
        return new NotificationAction(labelOf(notification.getEntityType()), route);
    }

    private String routeOf(String entityType, UUID entityId) {
        if (entityType == null || entityId == null) {
            return FALLBACK_ROUTE;
        }
        return switch (entityType) {
            case "CONTRACT_PERIOD" -> "/contract-periods/" + entityId;
            case "CONTRACT" -> "/contracts/" + entityId;
            case "TICKET" -> "/tickets/" + entityId;
            case "TIMER" -> "/timers/abandoned";
            case "WORK_LOG" -> "/work-logs/" + entityId;
            // Um tipo sem rota conhecida leva à central, e não a um caminho inventado que
            // resultaria em 404 no cliente.
            default -> FALLBACK_ROUTE;
        };
    }

    private String labelOf(String entityType) {
        if (entityType == null) {
            return "Ver notificações";
        }
        return switch (entityType) {
            case "CONTRACT_PERIOD" -> "Ver extrato do período";
            case "CONTRACT" -> "Ver contrato";
            case "TICKET" -> "Ver ticket";
            case "TIMER" -> "Ver cronômetros abandonados";
            case "WORK_LOG" -> "Ver registro de horas";
            default -> "Ver notificações";
        };
    }
}
