package com.devtime.shared.mail;

/**
 * Porta de saída de e-mail (integrations.md §6.1).
 *
 * <p>O adapter é escolhido por perfil: {@code LoggingMailAdapter} em {@code local} e {@code test},
 * {@code SmtpMailAdapter} em {@code staging} e {@code prod} (T-001-68).
 *
 * <p><b>Nunca lança.</b> O envio ocorre depois do commit (TX-06, CP-10) e AQ-09 exige que a
 * indisponibilidade do provedor não desfaça nem interrompa a operação de negócio: o cadastro
 * persiste com {@code verificationEmailSent = false} e a UI oferece reenvio (CX-12). Uma exceção
 * propagada daqui transformaria uma degradação prevista em erro de requisição.
 */
public interface MailPort {

    /**
     * @return {@code true} quando o provedor aceitou a mensagem; {@code false} em qualquer falha,
     *     já registrada em log com nível {@code WARN} e sem o corpo da mensagem (§28 de spec 001)
     */
    boolean send(MailMessage message);
}
