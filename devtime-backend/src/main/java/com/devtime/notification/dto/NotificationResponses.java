package com.devtime.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs de saída da feature 013 (notifications.md §7 a §9). */
public final class NotificationResponses {

    private NotificationResponses() {}

    /**
     * NT-03: toda notificação leva a uma ação concreta.
     *
     * <p>{@code route} é o caminho do <b>frontend</b>, não da API: quem clica quer chegar à tela.
     */
    @Schema(name = "NotificationAction")
    public record NotificationAction(String label, String route) {}

    /**
     * Notificação como exibida na central.
     *
     * <p>CP-11: {@code dedupeKey} <b>não</b> é exposto. É detalhe interno, e expô-lo convidaria à
     * manipulação de um valor cuja unicidade é a garantia da feature.
     *
     * @param body texto completo e autoexplicativo — a notificação não deve exigir ser aberta para
     *     ser entendida (§7)
     */
    @Schema(name = "NotificationResponse")
    public record NotificationResponse(
            UUID id,
            String type,
            String severity,
            String title,
            String body,
            Map<String, Object> payload,
            String entityType,
            UUID entityId,
            NotificationAction action,
            Instant readAt,
            Instant emailSentAt,
            Instant createdAt) {}

    /** §7.1: endpoint leve, consultado ao carregar toda tela. */
    @Schema(name = "UnreadCountResponse")
    public record UnreadCountResponse(long unreadCount, Map<String, Long> bySeverity) {}

    /** §8.1: a resposta traz a contagem atualizada, evitando uma segunda requisição. */
    @Schema(name = "NotificationReadResponse")
    public record NotificationReadResponse(UUID id, Instant readAt, long unreadCount) {}

    /** §8.2. */
    @Schema(name = "MarkAllReadResponse")
    public record MarkAllReadResponse(int markedCount, long unreadCount) {}

    /**
     * Tipo disponível na tela de preferências (§9.1).
     *
     * <p>{@code availableTypes} existe para que a interface liste os tipos sem replicar o catálogo
     * — adicionar um tipo não deve exigir alterar o frontend.
     *
     * @param canMute {@code false} nos tipos críticos; silenciá-los contrariaria o propósito do
     *     produto (§9.1)
     */
    @Schema(name = "NotificationTypeOption")
    public record NotificationTypeOption(
            String type, String label, String severity, boolean canMute) {}

    /**
     * Preferências do destinatário (§9.1).
     *
     * <p>O nome do campo é {@code mutedNotificationTypes}, como em entities.md §6.2.1 e em {@code
     * GET /auth/me} — §9.1 de notifications.md o chama de {@code mutedTypes}, e a divergência está
     * registrada no {@code CHANGELOG.md}. Manter um único nome evita que a mesma preferência tenha
     * duas grafias na API.
     */
    @Schema(name = "NotificationPreferencesResponse")
    public record NotificationPreferencesResponse(
            boolean emailNotifications,
            List<String> mutedNotificationTypes,
            List<NotificationTypeOption> availableTypes) {}

    /**
     * Evento do fluxo SSE (§7.2).
     *
     * <p>Deliberadamente mínimo: o fluxo informa <b>que</b> algo aconteceu e a nova contagem; o
     * detalhe vem da listagem. Enviar o objeto completo duplicaria o contrato e obrigaria a
     * mantê-lo sincronizado com {@code GET /notifications}.
     *
     * <p>ST-05 / INV-NOT-04: o fluxo <b>nunca</b> é o único canal — o histórico é sempre a fonte.
     */
    @Schema(name = "StreamEventDto")
    public record StreamEventDto(
            UUID id, String type, String severity, String title, long unreadCount) {}
}
