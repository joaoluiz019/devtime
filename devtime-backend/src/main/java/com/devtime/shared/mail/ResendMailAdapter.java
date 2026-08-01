package com.devtime.shared.mail;

import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.observability.SensitiveDataMasker;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Adapter de e-mail sobre a API transacional do Resend (integrations.md §6.1: adapter "substituível
 * por provedor de API transacional").
 *
 * <p>Ativo em {@code staging} e {@code prod} quando {@code devtime.mail.provider=resend}. É
 * mutuamente exclusivo com {@link SmtpMailAdapter}: dois beans de {@link MailPort} no mesmo
 * contexto quebrariam a injeção em {@code AuthMailNotifier}, então a escolha é feita por
 * configuração e verificada na inicialização, não em tempo de execução.
 *
 * <p>Envia HTML e texto puro na mesma mensagem (ML-06). A chave de API vem de {@code
 * RESEND_API_KEY} (ART-083) e nunca aparece em log — nem mascarada, porque o adapter não a registra
 * em nenhum caminho.
 *
 * <p>CE-I-08: voltar para SMTP é trocar o valor de {@code MAIL_PROVIDER}, sem tocar em código de
 * feature.
 */
@Component
@Profile({"staging", "prod"})
@ConditionalOnProperty(name = "devtime.mail.provider", havingValue = "resend")
@Slf4j
public class ResendMailAdapter implements MailPort {

    private final Resend resend;
    private final MailTemplateRenderer renderer;
    private final String from;

    public ResendMailAdapter(
            Resend resend, MailTemplateRenderer renderer, DevTimeProperties properties) {
        this.resend = resend;
        this.renderer = renderer;
        // CF-01: a propriedade vem do bloco tipado e validado, nunca de um @Value isolado.
        this.from = properties.mail().from();
    }

    @Override
    public boolean send(MailMessage message) {
        try {
            MailTemplateRenderer.RenderedMail rendered = renderer.render(message);
            CreateEmailOptions options =
                    CreateEmailOptions.builder()
                            .from(from)
                            .to(message.to())
                            .subject(rendered.subject())
                            .html(rendered.html())
                            // ML-06: a variante em texto puro acompanha o HTML na mesma
                            // mensagem, para clientes que não renderizam HTML.
                            .text(rendered.plainText())
                            .build();

            CreateEmailResponse response = resend.emails().send(options);

            // O identificador do provedor é registrado para reconciliar entrega no painel do
            // Resend. Não é dado pessoal, ao contrário do destinatário, que vai mascarado.
            log.info(
                    "e-mail aceito pelo provedor destinatario={} tipo={} providerId={}",
                    SensitiveDataMasker.mask(message.to()),
                    message.template().name(),
                    response == null ? "desconhecido" : response.getId());
            return true;
        } catch (Exception e) {
            // Captura ampla proposital, idêntica ao SmtpMailAdapter: qualquer falha de envio é
            // degradação prevista (AQ-09), não erro da requisição de negócio. O contrato de
            // MailPort é não lançar. O corpo da mensagem não vai para o log (§28 de spec 001).
            log.warn(
                    "falha no envio de e-mail destinatario={} tipo={} causa={}",
                    SensitiveDataMasker.mask(message.to()),
                    message.template().name(),
                    e.getClass().getSimpleName());
            return false;
        }
    }
}
