package com.devtime.notification;

import com.devtime.notification.domain.NotificationType;
import com.devtime.user.UserAccountService;
import com.devtime.user.dto.UserAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decisão de envio de e-mail pelas preferências do destinatário (RN-608).
 *
 * <p><b>Esta política decide apenas sobre o e-mail.</b> A notificação in-app já foi criada quando
 * ela é consultada, e é isso que RN-608 significa: preferência silencia o canal externo, nunca o
 * histórico. Um alerta que o usuário optou por não receber por e-mail continua consultável na
 * central (NT-01, INV-NOT-02).
 *
 * <p>Duas chaves independentes, ambas verificadas: {@code emailNotifications} desliga tudo (FA-07);
 * {@code mutedNotificationTypes} desliga um tipo (FA-06).
 *
 * <p>§9.1: <b>tipos críticos não podem ser silenciados</b>. Um contrato excedido tem impacto
 * financeiro direto e um anexo infectado é incidente de segurança — o silenciamento é recusado na
 * escrita da preferência ({@code DEVTIME-4001}), e aqui a verificação é a segunda barreira, para o
 * caso de uma preferência antiga ter registrado o tipo antes de ele virar crítico.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDispatchPolicy {

    private static final String KEY_EMAIL_ENABLED = "emailNotifications";
    private static final String KEY_MUTED = "mutedNotificationTypes";

    private final UserAccountService userAccountService;
    private final ObjectMapper objectMapper;

    /** Motivo da supressão, usado na métrica {@code notification.email.suppressed}. */
    public enum SuppressionReason {
        /** {@code emailNotifications = false} (FA-07). */
        EMAIL_DISABLED,
        /** Tipo em {@code mutedNotificationTypes} (FA-06). */
        TYPE_MUTED,
        /** Conta inexistente ou sem endereço — nada a enviar. */
        NO_RECIPIENT_ADDRESS
    }

    /** Resultado da avaliação: o endereço quando permitido, ou o motivo da supressão. */
    public record Decision(boolean allowed, String emailAddress, SuppressionReason reason) {

        static Decision allow(String emailAddress) {
            return new Decision(true, emailAddress, null);
        }

        static Decision suppress(SuppressionReason reason) {
            return new Decision(false, null, reason);
        }
    }

    public Decision evaluate(UUID recipientId, NotificationType type) {
        Optional<UserAccount> account = userAccountService.findById(recipientId);
        if (account.isEmpty() || account.get().email() == null || account.get().email().isBlank()) {
            return Decision.suppress(SuppressionReason.NO_RECIPIENT_ADDRESS);
        }

        Map<String, Object> preferences = readPreferences(account.get().preferences());

        if (!type.isCanMute()) {
            // §9.1: crítico ignora a preferência. É a única exceção, e ela existe porque o custo de
            // não avisar é maior que o incômodo de avisar.
            return Decision.allow(account.get().email());
        }
        if (!emailEnabled(preferences)) {
            return Decision.suppress(SuppressionReason.EMAIL_DISABLED); // FA-07
        }
        if (mutedTypes(preferences).contains(type.name())) {
            return Decision.suppress(SuppressionReason.TYPE_MUTED); // FA-06
        }
        return Decision.allow(account.get().email());
    }

    /** entities.md §6.2.1: o padrão é {@code true} — a chave ausente não silencia nada. */
    private boolean emailEnabled(Map<String, Object> preferences) {
        Object value = preferences.get(KEY_EMAIL_ENABLED);
        return !(value instanceof Boolean enabled) || enabled;
    }

    private Set<String> mutedTypes(Map<String, Object> preferences) {
        if (!(preferences.get(KEY_MUTED) instanceof List<?> values)) {
            return Set.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(value -> ((String) value).toUpperCase())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPreferences(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException unreadable) {
            // ER-08: degradar para os padrões é preferível a suprimir um alerta por causa de um
            // JSON corrompido — o padrão de entities.md §6.2.1 é receber o e-mail.
            log.warn("preferências ilegíveis; aplicando os padrões de entities.md §6.2.1");
            return Map.of();
        }
    }
}
