package com.devtime.shared.mail;

import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.observability.SensitiveDataMasker;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Adapter SMTP (integrations.md §6.1: {@code SmtpMailAdapter}, SMTP com TLS).
 *
 * <p>Ativo quando {@code devtime.mail.provider=smtp}, em qualquer perfil. As credenciais vêm de
 * variável de ambiente (ART-083); a ausência de configuração faz a aplicação falhar na
 * inicialização, não no primeiro envio.
 *
 * <p>CE-I-08: trocar de provedor é substituir esta classe por outra implementação de {@link
 * MailPort}, sem tocar em nenhuma feature. {@link ResendMailAdapter} é a alternativa; a seleção é
 * feita por {@code devtime.mail.provider}. O padrão deixou de ser SMTP: sem valor explícito quem
 * atende é {@link LoggingMailAdapter}, porque um ambiente sem configuração de e-mail deve registrar
 * o envio, e não tentar entregar por um host que ninguém declarou.
 */
@Component
@ConditionalOnProperty(name = "devtime.mail.provider", havingValue = "smtp")
@Slf4j
public class SmtpMailAdapter implements MailPort {

    private final JavaMailSender mailSender;
    private final MailTemplateRenderer renderer;
    private final String from;

    public SmtpMailAdapter(
            JavaMailSender mailSender,
            MailTemplateRenderer renderer,
            DevTimeProperties properties) {
        this.mailSender = mailSender;
        this.renderer = renderer;
        // CF-01: a propriedade vem do bloco tipado e validado, nunca de um @Value isolado.
        this.from = properties.mail().from();
    }

    @Override
    public boolean send(MailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            // multipart=true: ML-06 exige as duas variantes na mesma mensagem, para clientes que
            // não renderizam HTML.
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            MailTemplateRenderer.RenderedMail rendered = renderer.render(message);
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(rendered.subject());
            helper.setText(rendered.plainText(), rendered.html());
            mailSender.send(mime);
            return true;
        } catch (Exception e) {
            // Captura ampla proposital: qualquer falha de envio é degradação prevista (AQ-09), não
            // erro da requisição de negócio. O contrato de MailPort é não lançar.
            log.warn(
                    "falha no envio de e-mail destinatario={} tipo={} causa={} detalhe={}",
                    SensitiveDataMasker.mask(message.to()),
                    message.template().name(),
                    e.getClass().getSimpleName(),
                    SensitiveDataMasker.mask(e.getMessage()));
            return false;
        }
    }
}
