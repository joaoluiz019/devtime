package com.devtime.tenant;

import com.devtime.tenant.dto.TenantRequests.TenantSettingsRequest;
import com.devtime.tenant.dto.TenantSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mescla, serialização e diferença das 10 chaves operacionais (users.md §6.2).
 *
 * <p>Separado de {@code TenantServiceImpl} porque a mescla de atualização parcial, a serialização
 * para {@code JSONB} e o cálculo do diff de auditoria são três operações mecânicas sobre a mesma
 * estrutura — mantê-las no serviço o levaria muito além de BR-010 sem acrescentar regra alguma.
 *
 * <p>{@code TenantSettingsService} continua sendo a porta de <b>leitura</b> (CP-07); esta classe é
 * interna à feature e não é alcançada por nenhuma outra.
 */
@Component
@RequiredArgsConstructor
public class TenantSettingsWriter {

    private final TenantSettingsValidator validator;
    private final ObjectMapper objectMapper;

    /** Aplica sobre o valor atual apenas as chaves informadas na requisição. */
    public TenantSettings merge(TenantSettings current, TenantSettingsRequest request) {
        return new TenantSettings(
                request.workDayMinutes() == null
                        ? current.workDayMinutes()
                        : request.workDayMinutes(),
                request.workDays() == null ? current.workDays() : List.copyOf(request.workDays()),
                request.defaultRolloverPolicy() == null
                        ? current.defaultRolloverPolicy()
                        : request.defaultRolloverPolicy(),
                request.defaultOveragePolicy() == null
                        ? current.defaultOveragePolicy()
                        : request.defaultOveragePolicy(),
                request.timerLongRunningMinutes() == null
                        ? current.timerLongRunningMinutes()
                        : request.timerLongRunningMinutes(),
                request.timerAutoAbandonMinutes() == null
                        ? current.timerAutoAbandonMinutes()
                        : request.timerAutoAbandonMinutes(),
                request.allowFutureWorkLogs() == null
                        ? current.allowFutureWorkLogs()
                        : request.allowFutureWorkLogs(),
                request.retroactiveLimitDays() == null
                        ? current.retroactiveLimitDays()
                        : request.retroactiveLimitDays(),
                request.roundingMinutes() == null
                        ? current.roundingMinutes()
                        : request.roundingMinutes(),
                request.notificationThresholds() == null
                        ? current.notificationThresholds()
                        // CX-10: ordenada e sem duplicatas antes de persistir.
                        : validator.normalizeThresholds(request.notificationThresholds()));
    }

    /** §18: apenas as chaves que de fato mudaram entram na trilha. */
    public Map<String, Object> changedKeys(TenantSettings current, TenantSettings effective) {
        Map<String, Object> currentMap = asMap(current);
        Map<String, Object> changes = new LinkedHashMap<>();
        asMap(effective)
                .forEach(
                        (key, value) -> {
                            if (!Objects.equals(currentMap.get(key), value)) {
                                changes.put(key, value);
                            }
                        });
        return changes;
    }

    public Map<String, Object> previousValues(TenantSettings current, Set<String> keys) {
        Map<String, Object> currentMap = asMap(current);
        Map<String, Object> previous = new LinkedHashMap<>();
        keys.forEach(key -> previous.put(key, currentMap.get(key)));
        return previous;
    }

    public String serialize(TenantSettings settings) {
        try {
            return objectMapper.writeValueAsString(asMap(settings));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Falha ao serializar configurações do tenant", failure);
        }
    }

    private Map<String, Object> asMap(TenantSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("workDayMinutes", settings.workDayMinutes());
        map.put("workDays", settings.workDays());
        map.put("defaultRolloverPolicy", settings.defaultRolloverPolicy());
        map.put("defaultOveragePolicy", settings.defaultOveragePolicy());
        map.put("timerLongRunningMinutes", settings.timerLongRunningMinutes());
        map.put("timerAutoAbandonMinutes", settings.timerAutoAbandonMinutes());
        map.put("allowFutureWorkLogs", settings.allowFutureWorkLogs());
        map.put("retroactiveLimitDays", settings.retroactiveLimitDays());
        map.put("roundingMinutes", settings.roundingMinutes());
        map.put("notificationThresholds", settings.notificationThresholds());
        return map;
    }
}
