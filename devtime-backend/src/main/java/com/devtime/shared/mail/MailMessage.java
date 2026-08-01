package com.devtime.shared.mail;

import java.util.Map;

/**
 * Mensagem transacional a enviar.
 *
 * @param to destinatário; dado pessoal, mascarado em log (§9.2 de security.md)
 * @param template modelo a renderizar
 * @param variables valores de substituição. ML-08: apenas o estritamente necessário — nunca senha,
 *     hash ou dado de outro titular
 */
public record MailMessage(String to, MailTemplate template, Map<String, String> variables) {

    public MailMessage {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    /**
     * Modelos transacionais da feature 001 (T-001-37).
     *
     * <p>O assunto vive no enum, e não no template, porque é a única parte da mensagem que precisa
     * ser conhecida por quem apenas registra o envio em log.
     */
    public enum MailTemplate {
        /** Cadastro: link de verificação com validade de 7 dias. */
        EMAIL_VERIFICATION("mail/email-verification", "Confirme seu e-mail no DevTime"),
        /** RN-461: link de redefinição com validade de 1 hora. */
        PASSWORD_RESET("mail/password-reset", "Redefinição de senha no DevTime"),
        /** RN-454: confirmação de que a senha mudou — permite reagir a uma troca não solicitada. */
        PASSWORD_CHANGED("mail/password-changed", "Sua senha do DevTime foi alterada"),
        /** AU-06: alerta ao titular a cada bloqueio por tentativas de acesso. */
        ACCOUNT_LOCKED("mail/account-locked", "Alerta de segurança na sua conta DevTime"),
        /** RN-457: convite para uma organização, com validade de 7 dias. */
        INVITATION("mail/invitation", "Você foi convidado para uma organização no DevTime"),
        /**
         * RN-608: notificação da central entregue por e-mail (feature 013).
         *
         * <p>Modelo único para os 20 tipos do catálogo: o assunto e o corpo chegam como variáveis,
         * já renderizados por {@code NotificationTemplateRenderer}. Um arquivo por tipo obrigaria a
         * criar dois recursos a cada novo tipo, e a mensagem é a mesma estrutura em todos — título,
         * corpo e um link para a origem.
         *
         * <p>§19.1 / CP-15: o corpo <b>não</b> contém descrições de work log nem valores
         * monetários. O e-mail sai para um provedor externo e pode ser armazenado fora do controle
         * do tenant; ele informa e leva ao sistema, não reproduz o dado.
         */
        NOTIFICATION("mail/notification", "Você tem uma notificação no DevTime");

        private final String templateBase;
        private final String subject;

        MailTemplate(String templateBase, String subject) {
            this.templateBase = templateBase;
            this.subject = subject;
        }

        /** Base do recurso; ML-06 exige as variantes {@code .txt} e {@code .html}. */
        public String templateBase() {
            return templateBase;
        }

        public String subject() {
            return subject;
        }
    }
}
