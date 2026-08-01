package com.devtime.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * DTOs de entrada da feature 013.
 *
 * <p>Curtos por construção: <b>não existe DTO de criação</b> (CP-12, CA-15). Notificações nascem de
 * eventos de domínio, e uma rota de criação permitiria fabricar alertas.
 */
public final class NotificationRequests {

    private NotificationRequests() {}

    /**
     * Filtros da central (§7).
     *
     * <p>A ordenação não é parâmetro: é fixa em {@code createdAt DESC}. Uma central ordenada por
     * outro critério esconderia o alerta mais recente, que é justamente o que se busca ao abri-la.
     */
    public record NotificationFilter(
            Boolean read, String type, String severity, Instant createdFrom, Instant createdTo) {

        public static NotificationFilter empty() {
            return new NotificationFilter(null, null, null, null, null);
        }
    }

    /**
     * Alteração de preferências (§9.2).
     *
     * <p>Atualização parcial: um campo nulo significa "não altere". Interpretar nulo como
     * "desligue" faria uma chamada que só muda os tipos silenciados desligar o e-mail inteiro.
     *
     * @param mutedNotificationTypes tipos silenciados <b>para e-mail</b>; a notificação in-app
     *     continua sendo criada (NT-01, RN-608)
     */
    @Schema(name = "NotificationPreferencesRequest")
    public record NotificationPreferencesRequest(
            Boolean emailNotifications, List<String> mutedNotificationTypes) {}
}
