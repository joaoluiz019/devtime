package com.devtime.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.user.dto.UserPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Padrões e mescla de {@code users.preferences} (entities.md §6.2.1). */
class UserPreferencesCodecTest {

    private final UserPreferencesCodec codec = new UserPreferencesCodec(new ObjectMapper());

    @Test
    @DisplayName("Chaves ausentes recebem os padrões de §6.2.1 na leitura")
    void defaultsAreAppliedOnRead() {
        UserPreferences preferences = codec.read("{}");

        assertThat(preferences.theme()).isEqualTo(UserPreferences.THEME_SYSTEM);
        assertThat(preferences.dashboardPeriod()).isEqualTo(UserPreferences.PERIOD_CURRENT);
        assertThat(preferences.emailNotifications()).isTrue();
        assertThat(preferences.timerReminderEnabled()).isTrue();
        assertThat(preferences.mutedNotificationTypes()).isEmpty();
        assertThat(preferences.defaultCategoryId()).isNull();
    }

    @Test
    @DisplayName("JSON nulo ou ilegível degrada para os padrões, sem lançar")
    void malformedJsonFallsBackToDefaults() {
        assertThat(codec.read(null).theme()).isEqualTo(UserPreferences.THEME_SYSTEM);
        assertThat(codec.read("{quebrado").theme()).isEqualTo(UserPreferences.THEME_SYSTEM);
    }

    @Test
    @DisplayName("A escrita mescla: alterar o tema preserva as demais chaves")
    void mergePreservesUntouchedKeys() {
        String current = "{\"theme\":\"DARK\",\"emailNotifications\":false}";

        String merged = codec.merge(current, Map.of("theme", UserPreferences.THEME_LIGHT));

        UserPreferences preferences = codec.read(merged);
        assertThat(preferences.theme()).isEqualTo(UserPreferences.THEME_LIGHT);
        assertThat(preferences.emailNotifications()).isFalse();
    }

    @Test
    @DisplayName("Chave desconhecida por esta versão do código é preservada na mescla")
    void unknownKeysSurviveMerge() {
        String merged = codec.merge("{\"futura\":\"valor\"}", Map.of("theme", "DARK"));

        assertThat(merged).contains("futura").contains("DARK");
    }
}
