package com.devtime.shared.observability;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Máscara de dados sensíveis em mensagens de log (ART-084, security.md §9.2).
 *
 * <p>A ausência de máscara em campo sensível é bloqueante em PR. Esta classe é a rede de segurança
 * <b>complementar</b> à revisão de código, não substituta: a regra primária continua sendo não
 * registrar o dado. Um filtro no appender protege contra o esquecimento; ele não autoriza registrar
 * dado sensível de propósito.
 *
 * <p>Campos que <b>nunca</b> devem ser registrados em nenhuma circunstância — senha, qualquer
 * token, conteúdo de anexo, descrição de work log e valores monetários — não têm regra de máscara
 * parcial: a máscara é total, porque não existe fração segura deles.
 */
public final class SensitiveDataMasker {

    private static final String REDACTED = "***";

    /**
     * Valor de campo cujo nome indica dado crítico, em JSON ou em {@code chave=valor}.
     *
     * <p>{@code authorization} <b>não</b> consta aqui de propósito: este padrão para no primeiro
     * espaço, então mascararia apenas o esquema ({@code Bearer}) e deixaria o token exposto logo
     * depois. O cabeçalho é tratado por {@link #BEARER_TOKEN}, que preserva o esquema — útil no
     * diagnóstico — e mascara o token inteiro.
     */
    private static final Pattern CRITICAL_FIELD =
            Pattern.compile(
                    "(?i)(\"?(?:password|senha|passwordHash|token|accessToken|refreshToken|tokenHash"
                            + "|secret|jwtSecret)\"?\\s*[:=]\\s*\"?)([^\",;}\\s]+)");

    /** Cabeçalho {@code Authorization: Bearer <token>}. */
    private static final Pattern BEARER_TOKEN =
            Pattern.compile("(?i)(Bearer\\s+)([A-Za-z0-9._\\-]+)");

    /** JWT solto na mensagem: três segmentos Base64 URL-safe separados por ponto. */
    private static final Pattern JWT =
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");

    /** E-mail: preserva o domínio e os dois primeiros caracteres da parte local. */
    private static final Pattern EMAIL =
            Pattern.compile("\\b([A-Za-z0-9._%+-]{1,})@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b");

    /** CPF (11 dígitos) ou CNPJ (14 dígitos), com ou sem formatação. */
    private static final Pattern DOCUMENT = Pattern.compile("\\b(?:\\d[.\\-/]?){10,13}\\d\\b");

    private SensitiveDataMasker() {}

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = maskCriticalFields(message);
        masked = BEARER_TOKEN.matcher(masked).replaceAll("$1" + REDACTED);
        masked = JWT.matcher(masked).replaceAll(REDACTED);
        masked = maskDocuments(masked);
        return maskEmails(masked);
    }

    private static String maskCriticalFields(String message) {
        Matcher matcher = CRITICAL_FIELD.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    result, Matcher.quoteReplacement(matcher.group(1) + REDACTED));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String maskEmails(String message) {
        Matcher matcher = EMAIL.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String local = matcher.group(1);
            String domain = matcher.group(2);
            String visible = local.length() <= 2 ? local : local.substring(0, 2);
            matcher.appendReplacement(result, Matcher.quoteReplacement(visible + "****@" + domain));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Preserva apenas os 3 últimos dígitos, conforme o exemplo de security.md §9.2. */
    private static String maskDocuments(String message) {
        Matcher matcher = DOCUMENT.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String raw = matcher.group();
            String digits = raw.replaceAll("\\D", "");
            if (digits.length() != 11 && digits.length() != 14) {
                continue;
            }
            String suffix = digits.substring(digits.length() - 3);
            matcher.appendReplacement(result, Matcher.quoteReplacement(REDACTED + suffix));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
