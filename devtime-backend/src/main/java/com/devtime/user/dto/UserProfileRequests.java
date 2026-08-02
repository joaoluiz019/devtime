package com.devtime.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Entradas de perfil e preferências (users.md §5.1 e §5.2). */
public final class UserProfileRequests {

    private UserProfileRequests() {}

    /**
     * Atualização parcial do perfil (users.md §5.1).
     *
     * <p>Todo campo nulo preserva o valor atual. O e-mail está deliberadamente ausente: RS-01 o
     * torna não alterável no MVP, porque mudá-lo exigiria reverificação do endereço e invalidação
     * dos convites pendentes emitidos para o antigo.
     *
     * @param timezone preferência <b>pessoal</b>; afeta apenas exibição, nunca cálculo (CE-11,
     *     ART-031)
     */
    @Schema(name = "UserProfileUpdateRequest")
    public record UserProfileUpdateRequest(
            @Size(min = 2, max = 150) String fullName,
            @Size(min = 1, max = 60) String displayName,
            @Size(max = 60) String timezone,
            @Size(max = 10) String locale) {}

    /**
     * Atualização parcial das preferências (users.md §5.2).
     *
     * <p>BR-103: a coerência dos valores de enumeração é verificada por {@code @AssertTrue} no
     * próprio record — são listas fechadas de {@code entities.md} §6.2.1, e deixá-las chegar ao
     * serviço apenas adiaria a mesma rejeição.
     */
    @Schema(name = "UserPreferencesRequest")
    public record UserPreferencesRequest(
            String theme,
            UUID defaultCategoryId,
            String dashboardPeriod,
            Boolean emailNotifications,
            List<String> mutedNotificationTypes,
            Boolean timerReminderEnabled) {

        @AssertTrue(message = "Tema inválido")
        @Schema(hidden = true)
        public boolean isThemeSupported() {
            return theme == null || UserPreferences.THEMES.contains(theme);
        }

        @AssertTrue(message = "Período do painel inválido")
        @Schema(hidden = true)
        public boolean isDashboardPeriodSupported() {
            return dashboardPeriod == null
                    || UserPreferences.DASHBOARD_PERIODS.contains(dashboardPeriod);
        }
    }
}
