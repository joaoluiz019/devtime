package com.devtime.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Preferências de interface do usuário, já tipadas (entities.md §6.2.1).
 *
 * <p>Mesma decisão de {@code TenantSettings}: a coluna é {@code JSONB} para evoluir sem migration,
 * mas quem consome precisa de valores tipados e com padrão aplicado. Um {@code theme} nulo chegando
 * ao cliente faria cada tela decidir sozinha qual é o padrão.
 *
 * @param theme {@code LIGHT}, {@code DARK} ou {@code SYSTEM}
 * @param defaultCategoryId 3º elo da cadeia de RN-104; nulo quando o usuário não escolheu
 * @param dashboardPeriod recorte inicial do painel
 * @param emailNotifications §9.2 de notifications.md; escrito também por {@code 013}
 * @param mutedNotificationTypes tipos silenciados; {@code CRITICAL} nunca entra aqui (RN-604)
 * @param timerReminderEnabled RN-163: lembrete de cronômetro longo
 */
@Schema(name = "UserPreferences")
public record UserPreferences(
        String theme,
        UUID defaultCategoryId,
        String dashboardPeriod,
        boolean emailNotifications,
        List<String> mutedNotificationTypes,
        boolean timerReminderEnabled) {

    public static final String THEME_LIGHT = "LIGHT";
    public static final String THEME_DARK = "DARK";
    public static final String THEME_SYSTEM = "SYSTEM";

    public static final String PERIOD_CURRENT = "CURRENT_PERIOD";
    public static final String PERIOD_LAST_7 = "LAST_7_DAYS";
    public static final String PERIOD_LAST_30 = "LAST_30_DAYS";

    public static final List<String> THEMES = List.of(THEME_LIGHT, THEME_DARK, THEME_SYSTEM);
    public static final List<String> DASHBOARD_PERIODS =
            List.of(PERIOD_CURRENT, PERIOD_LAST_7, PERIOD_LAST_30);

    public UserPreferences {
        mutedNotificationTypes =
                mutedNotificationTypes == null ? List.of() : List.copyOf(mutedNotificationTypes);
    }

    /** Valores de entities.md §6.2.1, aplicados a toda chave ausente. */
    public static UserPreferences defaults() {
        return new UserPreferences(THEME_SYSTEM, null, PERIOD_CURRENT, true, List.of(), true);
    }
}
