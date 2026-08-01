package com.devtime.notification.dto;

import com.devtime.notification.domain.NotificationSeverity;
import com.devtime.notification.domain.NotificationType;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pedido de criação de notificação, montado pelos consumidores de evento.
 *
 * <p>Objeto único em vez de dez parâmetros (BR-012). Carrega o conjunto de destinatários já
 * resolvido e a chave já montada: a decisão de <b>quem</b> recebe é de {@link
 * com.devtime.notification.RecipientResolver} e a de <b>qual evento lógico é este</b> é de {@link
 * com.devtime.notification.DedupeKeyBuilder}, ambas antes do serviço.
 *
 * <p>Não existe DTO de request equivalente na API: <b>não há rota de criação</b> (CP-12, RS-05).
 * Notificações nascem exclusivamente de eventos de domínio, e uma rota permitiria a um usuário
 * fabricar alertas.
 *
 * @param severity pode divergir do padrão do tipo — {@code CONTRACT_USAGE} é {@code INFO} em 50% e
 *     {@code CRITICAL} em 100% (§6.1)
 * @param payload dados estruturados para renderização; §19.1 proíbe dado sensível
 * @param dedupeKeyFor chave por destinatário. É função, e não texto, porque §6.1 define formatos
 *     dos dois tipos: {@code CONTRACT_USAGE:{periodId}:{threshold}} é a mesma para todos, enquanto
 *     {@code ADJUSTMENT:{adjustmentId}:{userId}} e {@code TICKET_COMMENT:{commentId}:{userId}}
 *     incluem o destinatário. Um único texto obrigaria a criar um comando por pessoa
 */
public record NotificationCommand(
        Set<UUID> recipientIds,
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String body,
        Map<String, Object> payload,
        String entityType,
        UUID entityId,
        java.util.function.Function<UUID, String> dedupeKeyFor) {

    public NotificationCommand {
        recipientIds = recipientIds == null ? Set.of() : Set.copyOf(recipientIds);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /** Chave fixa: o mesmo evento lógico para todos os destinatários. */
    public static java.util.function.Function<UUID, String> sameKey(String dedupeKey) {
        return recipientId -> dedupeKey;
    }
}
