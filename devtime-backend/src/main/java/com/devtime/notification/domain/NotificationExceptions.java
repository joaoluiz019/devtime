package com.devtime.notification.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;

/**
 * Exceções da feature 013 (spec §27).
 *
 * <p>Lista curta por construção. Notificações não são criadas por usuário — não existe rota de
 * criação (CP-12) — e as operações de leitura são triviais. A ausência de exceções é consequência
 * direta de RN-601 tornar a duplicação um <b>caso normal</b>: {@code DEVTIME-2002} para recurso de
 * terceiro e três códigos de preferência e fluxo é tudo o que a feature produz.
 *
 * <p><b>Não existe erro de duplicação</b> (§12). Um código para isso obrigaria todos os chamadores
 * a tratar uma condição esperada.
 */
public final class NotificationExceptions {

    private NotificationExceptions() {}

    /**
     * §9.1 / §12: tentativa de silenciar um tipo crítico.
     *
     * <p>Um contrato excedido tem impacto financeiro direto e um anexo infectado é incidente de
     * segurança. Permitir silenciá-los contrariaria o propósito do produto e a responsabilidade
     * sobre os dados do usuário.
     */
    public static BusinessRuleException typeCannotBeMuted(NotificationType type) {
        return new NotificationValidationException(
                ErrorCode.NOTIFICATION_TYPE_NOT_MUTABLE,
                Map.of("field", "mutedNotificationTypes", "type", type.name()),
                "A notificação \"" + type.getLabel() + "\" não pode ser silenciada");
    }

    /** §17.1: nome que não corresponde a nenhum tipo do catálogo. */
    public static BusinessRuleException unknownType(String rawType) {
        return new NotificationValidationException(
                ErrorCode.VALIDATION_FAILED,
                Map.of("field", "mutedNotificationTypes", "value", String.valueOf(rawType)),
                "Tipo de notificação inválido");
    }

    /** ST-03: mais de três conexões simultâneas do mesmo usuário. */
    public static BusinessRuleException streamLimitReached(int maxConnections) {
        return new NotificationLimitException(
                ErrorCode.NOTIFICATION_STREAM_LIMIT,
                Map.of("maxConnections", maxConnections),
                "Limite de conexões simultâneas atingido");
    }

    /** §9.2 — {@code 422}. */
    public static final class NotificationValidationException extends BusinessRuleException {
        private NotificationValidationException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }

    /** ST-03 — {@code 429}. */
    public static final class NotificationLimitException extends BusinessRuleException {
        private NotificationLimitException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }
}
