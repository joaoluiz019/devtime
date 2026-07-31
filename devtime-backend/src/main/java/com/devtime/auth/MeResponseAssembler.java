package com.devtime.auth;

import com.devtime.auth.dto.AuthResponses.MeMembership;
import com.devtime.auth.dto.AuthResponses.MeResponse;
import com.devtime.auth.dto.AuthResponses.MeTenant;
import com.devtime.auth.dto.AuthResponses.MeUser;
import com.devtime.tenant.dto.TenantViews.MembershipView;
import com.devtime.tenant.dto.TenantViews.TenantOption;
import com.devtime.tenant.dto.TenantViews.TenantView;
import com.devtime.user.dto.UserAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Montagem de {@code GET /auth/me} ({@code authentication.md} §5.10).
 *
 * <p>Separado de {@link AuthSessionAssembler} porque resolve um problema diferente: transformar as
 * colunas {@code JSONB} de preferências e configurações em objeto de resposta, com os padrões de
 * {@code entities.md} §6.1.1 e §6.2.1 aplicados às chaves ausentes.
 *
 * <p>Os padrões são mesclados no servidor, e não assumidos pelo cliente: um tenant criado antes da
 * introdução de uma chave nova a receberia ausente, e cada cliente teria de conhecer o valor padrão
 * — regra de domínio vazando para a apresentação.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeResponseAssembler {

    /** entities.md §6.2.1. */
    private static final Map<String, Object> DEFAULT_PREFERENCES =
            Map.of(
                    "theme",
                    "SYSTEM",
                    "dashboardPeriod",
                    "CURRENT_PERIOD",
                    "emailNotifications",
                    true,
                    "mutedNotificationTypes",
                    List.of(),
                    "timerReminderEnabled",
                    true);

    /** entities.md §6.1.1. */
    private static final Map<String, Object> DEFAULT_SETTINGS =
            Map.ofEntries(
                    Map.entry("workDayMinutes", 480),
                    Map.entry("workDays", List.of(1, 2, 3, 4, 5)),
                    Map.entry("defaultRolloverPolicy", "NONE"),
                    Map.entry("defaultOveragePolicy", "WARN"),
                    Map.entry("timerLongRunningMinutes", 480),
                    Map.entry("timerAutoAbandonMinutes", 960),
                    Map.entry("allowFutureWorkLogs", false),
                    Map.entry("retroactiveLimitDays", 30),
                    Map.entry("roundingMinutes", 0),
                    Map.entry("notificationThresholds", List.of(50, 80, 100)));

    private final AuthSessionAssembler sessionAssembler;
    private final ObjectMapper objectMapper;

    public MeResponse assemble(
            UserAccount account,
            TenantView tenant,
            MembershipView membership,
            List<TenantOption> availableTenants) {
        return new MeResponse(
                toUser(account, tenant),
                toTenant(tenant),
                new MeMembership(membership.id(), membership.role(), membership.status().name()),
                sessionAssembler.permissionsOf(membership.role()),
                sessionAssembler.toOptions(availableTenants));
    }

    private MeUser toUser(UserAccount account, TenantView tenant) {
        return new MeUser(
                account.id(),
                account.email(),
                account.fullName(),
                account.displayName(),
                account.avatarUrl(),
                // entities.md §6.2: fuso e idioma pessoais herdam do tenant quando não definidos.
                account.timezone() == null ? tenant.timezone() : account.timezone(),
                account.locale() == null ? tenant.locale() : account.locale(),
                merge(DEFAULT_PREFERENCES, account.preferences()));
    }

    private MeTenant toTenant(TenantView tenant) {
        return new MeTenant(
                tenant.id(),
                tenant.name(),
                tenant.slug(),
                tenant.timezone(),
                tenant.currency(),
                tenant.locale(),
                tenant.logoUrl(),
                tenant.status().name(),
                tenant.planCode(),
                merge(DEFAULT_SETTINGS, tenant.settings()));
    }

    private Map<String, Object> merge(Map<String, Object> defaults, String json) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults);
        merged.putAll(parse(json));
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException unreadable) {
            // Degradar para os padrões é preferível a falhar /auth/me: um JSON corrompido tornaria
            // a aplicação inteira inacessível para o titular, sem que ele tenha como corrigi-lo.
            log.warn("JSON de configuração ilegível tamanho={}", json.length());
            return Map.of();
        }
    }
}
