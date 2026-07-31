package com.devtime.auth;

import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.auth.event.AuthEvents.PasswordResetRequestedEvent;
import com.devtime.auth.event.AuthEvents.UserRegisteredEvent;
import com.devtime.auth.event.AuthEvents.VerificationResentEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Captura os valores brutos de token publicados nos eventos de autenticação.
 *
 * <p>Existe porque RT-02 é levado a sério: apenas o SHA-256 é persistido, e o valor bruto vive
 * exatamente onde o e-mail o encontraria — no evento. Um teste que lesse a coluna precisaria
 * reverter o hash; um que recalculasse o token estaria provando que ele é previsível, que é o
 * oposto do requisito.
 *
 * <p>{@code @EventListener} simples, e não {@code AFTER_COMMIT}: a captura precisa ocorrer no
 * instante da publicação, independentemente de a transação do teste confirmar depois.
 */
@Component
public class CapturedAuthEvents {

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    /** Índice por e-mail, para testes de API que só conhecem o endereço informado. */
    private static final Map<String, String> TOKENS_BY_EMAIL = new ConcurrentHashMap<>();

    public static String tokenForEmail(String email) {
        String token = TOKENS_BY_EMAIL.get(email.toLowerCase(java.util.Locale.ROOT));
        if (token == null) {
            throw new AssertionError("nenhum token de verificação capturado para " + email);
        }
        return token;
    }

    static String tokenFor(UUID userId, VerificationTokenType type) {
        String token = TOKENS.get(key(userId, type));
        if (token == null) {
            throw new AssertionError(
                    "nenhum token de " + type + " capturado para o usuário " + userId);
        }
        return token;
    }

    @EventListener
    void onUserRegistered(UserRegisteredEvent event) {
        TOKENS.put(
                key(event.userId(), VerificationTokenType.EMAIL_VERIFICATION),
                event.rawVerificationToken());
        TOKENS_BY_EMAIL.put(
                event.email().toLowerCase(java.util.Locale.ROOT), event.rawVerificationToken());
    }

    @EventListener
    void onVerificationResent(VerificationResentEvent event) {
        TOKENS.put(
                key(event.userId(), VerificationTokenType.EMAIL_VERIFICATION),
                event.rawVerificationToken());
        TOKENS_BY_EMAIL.put(
                event.email().toLowerCase(java.util.Locale.ROOT), event.rawVerificationToken());
    }

    @EventListener
    void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        TOKENS.put(
                key(event.userId(), VerificationTokenType.PASSWORD_RESET), event.rawResetToken());
    }

    private static String key(UUID userId, VerificationTokenType type) {
        return userId + ":" + type;
    }
}
