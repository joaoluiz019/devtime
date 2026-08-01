package com.devtime.notification.domain;

/**
 * Urgência da notificação (§3 de notifications.md).
 *
 * <p>NT-04: {@code CRITICAL} é reservado a situações com <b>impacto financeiro direto ou incidente
 * de segurança</b>. Inflacionar a severidade destrói o seu significado — se tudo é crítico, nada é,
 * e o usuário passa a ignorar exatamente o alerta que precisava ver.
 *
 * <p>É também o que decide o que pode ser silenciado: §9.1 de notifications.md fixa {@code canMute
 * = false} para os tipos críticos.
 */
public enum NotificationSeverity {
    INFO,
    WARNING,
    CRITICAL
}
