package com.devtime.tenant;

import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.tenant.domain.Tenant;
import com.devtime.tenant.dto.TenantSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversão de {@code tenant.settings} para {@link TenantSettings} (ver {@link
 * TenantSettingsService}).
 *
 * <p>Sem {@code @PreAuthorize}: a configuração é lida no meio de operações que já verificaram a
 * própria permissão (registrar horas, iniciar cronômetro), e exigir permissão aqui obrigaria toda
 * feature consumidora a conceder uma permissão de leitura de tenant a papéis que só precisam
 * registrar o próprio trabalho. Nenhuma rota HTTP alcança este serviço.
 *
 * <p>JSON ilegível degrada para os padrões em vez de propagar: a alternativa tornaria impossível
 * registrar horas em um tenant cuja coluna foi corrompida, sem que o usuário tenha como corrigi-la
 * (ER-08 — falha em operação não essencial degrada).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TenantSettingsServiceImpl implements TenantSettingsService {

    private final TenantRepository repository;
    private final TenantContext tenantContext;
    private final ObjectMapper objectMapper;

    @Override
    public TenantSettings settingsOf(UUID tenantId) {
        Tenant tenant =
                repository
                        .findById(tenantId)
                        .orElseThrow(() -> EntityNotFoundException.of(Tenant.class, tenantId));
        return toSettings(parse(tenant.getSettings()));
    }

    @Override
    public TenantSettings current() {
        return settingsOf(tenantContext.requireTenantId());
    }

    @Override
    public Map<String, Object> merged(String settingsJson) {
        Map<String, Object> merged = new LinkedHashMap<>(defaultsAsMap());
        merged.putAll(parse(settingsJson));
        return merged;
    }

    private TenantSettings toSettings(Map<String, Object> raw) {
        TenantSettings defaults = TenantSettings.defaults();
        return new TenantSettings(
                intOf(raw, "workDayMinutes", defaults.workDayMinutes()),
                intListOf(raw, "workDays", defaults.workDays()),
                stringOf(raw, "defaultRolloverPolicy", defaults.defaultRolloverPolicy()),
                stringOf(raw, "defaultOveragePolicy", defaults.defaultOveragePolicy()),
                intOf(raw, "timerLongRunningMinutes", defaults.timerLongRunningMinutes()),
                intOf(raw, "timerAutoAbandonMinutes", defaults.timerAutoAbandonMinutes()),
                booleanOf(raw, "allowFutureWorkLogs", defaults.allowFutureWorkLogs()),
                intOf(raw, "retroactiveLimitDays", defaults.retroactiveLimitDays()),
                intOf(raw, "roundingMinutes", defaults.roundingMinutes()),
                intListOf(raw, "notificationThresholds", defaults.notificationThresholds()));
    }

    private Map<String, Object> defaultsAsMap() {
        TenantSettings defaults = TenantSettings.defaults();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("workDayMinutes", defaults.workDayMinutes());
        map.put("workDays", defaults.workDays());
        map.put("defaultRolloverPolicy", defaults.defaultRolloverPolicy());
        map.put("defaultOveragePolicy", defaults.defaultOveragePolicy());
        map.put("timerLongRunningMinutes", defaults.timerLongRunningMinutes());
        map.put("timerAutoAbandonMinutes", defaults.timerAutoAbandonMinutes());
        map.put("allowFutureWorkLogs", defaults.allowFutureWorkLogs());
        map.put("retroactiveLimitDays", defaults.retroactiveLimitDays());
        map.put("roundingMinutes", defaults.roundingMinutes());
        map.put("notificationThresholds", defaults.notificationThresholds());
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException unreadable) {
            log.warn("JSON de configuração do tenant ilegível tamanho={}", json.length());
            return Map.of();
        }
    }

    private int intOf(Map<String, Object> raw, String key, int fallback) {
        Object value = raw.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean booleanOf(Map<String, Object> raw, String key, boolean fallback) {
        Object value = raw.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    private String stringOf(Map<String, Object> raw, String key, String fallback) {
        Object value = raw.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private List<Integer> intListOf(Map<String, Object> raw, String key, List<Integer> fallback) {
        if (!(raw.get(key) instanceof List<?> values)) {
            return fallback;
        }
        return values.stream()
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).intValue())
                .toList();
    }
}
