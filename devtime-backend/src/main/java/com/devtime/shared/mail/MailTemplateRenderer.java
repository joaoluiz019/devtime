package com.devtime.shared.mail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Renderização dos modelos de e-mail (ML-06: HTML e texto puro).
 *
 * <p>Substituição literal de {@code {{chave}}}, sem motor de template. A escolha é deliberada: os
 * modelos transacionais são fixos, e um motor com avaliação de expressão introduziria superfície de
 * injeção em mensagens que carregam nome informado pelo usuário.
 */
@Component
public class MailTemplateRenderer {

    /** Texto puro e HTML da mesma mensagem. */
    public record RenderedMail(String subject, String plainText, String html) {}

    public RenderedMail render(MailMessage message) {
        String plain = apply(load(message.template().templateBase() + ".txt"), message.variables());
        String html = apply(load(message.template().templateBase() + ".html"), message.variables());
        return new RenderedMail(message.template().subject(), plain, html);
    }

    private String load(String resourcePath) {
        try (InputStream input = new ClassPathResource(resourcePath).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Falha de empacotamento, não condição de execução: o modelo é recurso da aplicação.
            throw new IllegalStateException("Modelo de e-mail ausente: " + resourcePath, e);
        }
    }

    private String apply(String template, Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            rendered =
                    rendered.replace(
                            "{{" + variable.getKey() + "}}",
                            variable.getValue() == null ? "" : escape(variable.getValue()));
        }
        return rendered;
    }

    /**
     * Escapa os caracteres que teriam significado no corpo HTML.
     *
     * <p>Aplicado também à variante em texto puro: o custo é nenhum, e uma única função evita a
     * classe de erro em que a variante HTML deixa de ser escapada por engano.
     */
    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
