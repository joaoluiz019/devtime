package com.devtime.auth;

import com.devtime.auth.event.AuthEvents.AccountLockedEvent;
import com.devtime.auth.event.AuthEvents.PasswordChangedEvent;
import com.devtime.auth.event.AuthEvents.PasswordResetRequestedEvent;
import com.devtime.auth.event.AuthEvents.UserRegisteredEvent;
import com.devtime.auth.event.AuthEvents.VerificationResentEvent;
import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.mail.MailMessage;
import com.devtime.shared.mail.MailMessage.MailTemplate;
import com.devtime.shared.mail.MailPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envio dos e-mails transacionais da feature 001 (T-001-37).
 *
 * <p><b>Todos</b> os métodos são {@code AFTER_COMMIT} (TX-06, CP-10, BR-128). A razão é a mesma em
 * todos os casos: o e-mail é efeito colateral de um fato já decidido. Se o envio participasse da
 * transação, a indisponibilidade do provedor desfaria o cadastro (AQ-09, CX-12), o bloqueio de
 * segurança (RN-453) ou a troca de senha (RN-454) — cada um deles um resultado pior do que uma
 * mensagem não entregue.
 *
 * <p>{@link MailPort} nunca lança, então nenhuma falha aqui escapa para a requisição já concluída.
 */
@Component
@RequiredArgsConstructor
public class AuthMailNotifier {

    private final MailPort mailPort;
    private final AuthMetrics metrics;
    private final DevTimeProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        sendVerification(event.email(), event.fullName(), event.rawVerificationToken());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationResent(VerificationResentEvent event) {
        sendVerification(event.email(), event.fullName(), event.rawVerificationToken());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        send(
                MailTemplate.PASSWORD_RESET,
                event.email(),
                Map.of(
                        "fullName", event.fullName(),
                        "resetUrl", link("/auth/reset-password", event.rawResetToken())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChanged(PasswordChangedEvent event) {
        send(
                MailTemplate.PASSWORD_CHANGED,
                event.email(),
                Map.of(
                        "fullName", event.fullName(),
                        "changedAt", format(event.changedAt())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountLocked(AccountLockedEvent event) {
        // AU-06. Emitido apenas na transição para bloqueado (LoginFailureOutcome.justLocked), e não
        // a cada falha subsequente: AC-001-43 exige exatamente um alerta por bloqueio.
        send(
                MailTemplate.ACCOUNT_LOCKED,
                event.email(),
                Map.of(
                        "fullName", event.fullName(),
                        "lockedUntil", format(event.lockedUntil())));
    }

    private void sendVerification(String email, String fullName, String rawToken) {
        send(
                MailTemplate.EMAIL_VERIFICATION,
                email,
                Map.of("fullName", fullName, "verificationUrl", link("/auth/verify", rawToken)));
    }

    private void send(MailTemplate template, String to, Map<String, String> variables) {
        if (!mailPort.send(new MailMessage(to, template, variables))) {
            metrics.emailSendFailure(template.name()); // §29: auth.email.send_failures
        }
    }

    /**
     * Monta o link a partir de {@code APP_BASE_URL}.
     *
     * <p>A base vem de configuração e não do header {@code Host} da requisição: derivá-la da
     * requisição permitiria a um atacante disparar um cadastro com {@code Host} próprio e fazer o
     * sistema enviar, ao titular real, um link de verificação apontando para o domínio dele.
     */
    private String link(String path, String rawToken) {
        return properties.app().baseUrl()
                + path
                + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String format(Instant instant) {
        return instant == null ? "" : instant.toString();
    }
}
