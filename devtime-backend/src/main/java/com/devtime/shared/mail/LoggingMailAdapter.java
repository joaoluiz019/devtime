package com.devtime.shared.mail;

import com.devtime.shared.observability.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter de e-mail para desenvolvimento e testes (T-001-68).
 *
 * <p>Registra o envio sem contatar nenhum provedor: BR-203 proíbe teste que dependa de rede
 * externa, e o desenvolvimento local não deve exigir credenciais SMTP.
 *
 * <p>Ativo quando {@code devtime.mail.provider=logging}, e também quando a propriedade está
 * ausente: o padrão seguro é não enviar. Trocar o valor para {@code smtp} ou {@code resend} liga o
 * provedor real em qualquer perfil, inclusive {@code local} — o e-mail de verificação precisa ser
 * exercitável contra o provedor de verdade sem fingir que o ambiente é {@code staging}.
 *
 * <p>O corpo da mensagem <b>não</b> vai para o log (§28 de spec 001): o e-mail de verificação
 * contém o token, e registrá-lo transformaria o arquivo de log em um conjunto de credenciais de
 * ativação. O destinatário é mascarado (ART-084).
 */
@Component
@ConditionalOnProperty(
        name = "devtime.mail.provider",
        havingValue = "logging",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LoggingMailAdapter implements MailPort {

    private final MailTemplateRenderer renderer;

    @Override
    public boolean send(MailMessage message) {
        // Renderiza mesmo sem enviar: um modelo quebrado precisa falhar no ambiente de
        // desenvolvimento, e não apenas em produção, onde o adapter real o renderizaria.
        renderer.render(message);
        log.info(
                "e-mail simulado destinatario={} tipo={}",
                SensitiveDataMasker.mask(message.to()),
                message.template().name());
        return true;
    }
}
