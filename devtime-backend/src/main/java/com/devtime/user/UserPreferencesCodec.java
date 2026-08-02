package com.devtime.user;

import com.devtime.user.dto.UserPreferences;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Conversão entre o {@code JSONB} de {@code users.preferences} e {@link UserPreferences}.
 *
 * <p>Os padrões de {@code entities.md} §6.2.1 são aplicados na <b>leitura</b>, e não apenas na
 * escrita (checklist §34 da spec): uma conta criada antes da introdução de uma chave a receberia
 * ausente, e cada consumidor teria de conhecer o padrão — que foi exatamente o problema que {@code
 * TenantSettingsService} resolveu para o tenant.
 *
 * <p>A escrita é <b>mescla</b>, nunca substituição: chaves desconhecidas por esta versão do código
 * são preservadas. Sem isso, um cliente antigo apagaria silenciosamente uma preferência introduzida
 * depois dele.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserPreferencesCodec {

    static final String KEY_THEME = "theme";
    static final String KEY_DEFAULT_CATEGORY = "defaultCategoryId";
    static final String KEY_DASHBOARD_PERIOD = "dashboardPeriod";
    static final String KEY_EMAIL_NOTIFICATIONS = "emailNotifications";
    static final String KEY_MUTED_TYPES = "mutedNotificationTypes";
    static final String KEY_TIMER_REMINDER = "timerReminderEnabled";

    private final ObjectMapper objectMapper;

    public UserPreferences read(String json) {
        Map<String, Object> raw = readRaw(json);
        UserPreferences defaults = UserPreferences.defaults();
        return new UserPreferences(
                text(raw, KEY_THEME, defaults.theme()),
                uuid(raw.get(KEY_DEFAULT_CATEGORY)),
                text(raw, KEY_DASHBOARD_PERIOD, defaults.dashboardPeriod()),
                bool(raw, KEY_EMAIL_NOTIFICATIONS, defaults.emailNotifications()),
                stringList(raw.get(KEY_MUTED_TYPES)),
                bool(raw, KEY_TIMER_REMINDER, defaults.timerReminderEnabled()));
    }

    /**
     * Aplica sobre o JSON atual apenas as chaves informadas.
     *
     * @param changes chaves a sobrescrever; valores nulos já devem ter sido descartados por quem
     *     chama, pois nulo aqui significaria "gravar nulo"
     */
    public String merge(String currentJson, Map<String, Object> changes) {
        Map<String, Object> merged = readRaw(currentJson);
        merged.putAll(changes);
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Falha ao serializar preferências", failure);
        }
    }

    private Map<String, Object> readRaw(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                    json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException unreadable) {
            // ER-08: degradar para os padrões é preferível a impedir o usuário de ajustar
            // preferências de um JSON que ele não tem como corrigir.
            log.warn("Preferências ilegíveis; aplicando os padrões de entities.md §6.2.1");
            return new LinkedHashMap<>();
        }
    }

    private String text(Map<String, Object> raw, String key, String fallback) {
        Object value = raw.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private boolean bool(Map<String, Object> raw, String key, boolean fallback) {
        Object value = raw.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }
}
