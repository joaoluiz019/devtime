package com.devtime.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.config.DevTimeProperties.MailProps;
import com.devtime.shared.config.DevTimeProperties.MailProps.MailProvider;
import com.devtime.shared.mail.MailMessage.MailTemplate;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Adapter Resend (integrations.md §6.1).
 *
 * <p>BR-203: nenhuma chamada de rede. O cliente do Resend é dublê, então o teste verifica o
 * contrato de {@link MailPort} e o conteúdo montado, não o comportamento do provedor.
 */
class ResendMailAdapterTest {

    private static final String FROM = "nao-responda@devtime.test";

    private Resend resend;
    private Emails emails;
    private ResendMailAdapter adapter;

    @BeforeEach
    void setUp() {
        resend = mock(Resend.class);
        emails = mock(Emails.class);
        when(resend.emails()).thenReturn(emails);
        adapter = new ResendMailAdapter(resend, new MailTemplateRenderer(), properties());
    }

    @Test
    @DisplayName("ML-06: a mensagem enviada leva HTML e texto puro, com remetente de configuração")
    void shouldSendBothVariants() throws ResendException {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenReturn(new CreateEmailResponse("abc-123"));

        boolean sent = adapter.send(verificationTo("rafael@exemplo.com"));

        assertThat(sent).isTrue();
        ArgumentCaptor<CreateEmailOptions> captor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        org.mockito.Mockito.verify(emails).send(captor.capture());
        CreateEmailOptions options = captor.getValue();
        assertThat(options.getFrom()).isEqualTo(FROM);
        assertThat(options.getTo()).containsExactly("rafael@exemplo.com");
        assertThat(options.getSubject()).isEqualTo(MailTemplate.EMAIL_VERIFICATION.subject());
        assertThat(options.getHtml()).isNotBlank();
        assertThat(options.getText()).isNotBlank();
    }

    @Test
    @DisplayName("AQ-09: falha do provedor devolve false e não propaga exceção")
    void shouldNotThrowWhenProviderFails() throws ResendException {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("provedor indisponível"));

        assertThat(adapter.send(verificationTo("rafael@exemplo.com"))).isFalse();
    }

    @Test
    @DisplayName("O contrato de MailPort é não lançar, inclusive em falha inesperada do cliente")
    void shouldNotThrowOnUnexpectedFailure() {
        when(resend.emails()).thenThrow(new IllegalStateException("cliente em estado inválido"));

        assertThat(adapter.send(verificationTo("rafael@exemplo.com"))).isFalse();
    }

    private MailMessage verificationTo(String recipient) {
        return new MailMessage(
                recipient,
                MailTemplate.EMAIL_VERIFICATION,
                Map.of(
                        "fullName",
                        "Rafael Mendes",
                        "verificationUrl",
                        "http://localhost:4200/auth/verify?token=t"));
    }

    private DevTimeProperties properties() {
        return new DevTimeProperties(
                null,
                null,
                null,
                null,
                new MailProps(FROM, MailProvider.RESEND, "re_teste"),
                null,
                null,
                null);
    }
}
