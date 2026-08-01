package com.devtime.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração tipada da aplicação (backend.md §13.2).
 *
 * <p>CF-01: nenhuma propriedade é lida por {@code @Value} isolado. CF-03: a aplicação falha ao
 * iniciar se uma propriedade obrigatória estiver ausente — é a validação declarada aqui que produz
 * essa falha.
 *
 * <p>Os blocos {@code timer}, {@code report} e {@code storage} previstos em backend.md §13.2 não
 * existem ainda: pertencem às features 009, 012 e 015, fora do escopo desta sprint. Serão
 * adicionados quando houver quem os leia (CG-10).
 */
@ConfigurationProperties(prefix = "devtime")
@Validated
public record DevTimeProperties(
        @NotNull @Valid ApiProps api,
        @NotNull @Valid CorsProps cors,
        @NotNull @Valid SecurityProps security,
        @NotNull @Valid AppProps app,
        @NotNull @Valid MailProps mail) {

    /**
     * @param baseUrl {@code APP_BASE_URL} de integrations.md §12: origem dos links enviados por
     *     e-mail. Configurável e não derivada da requisição — derivá-la do header {@code Host}
     *     permitiria a um atacante fazer o sistema enviar, ao titular real, um link de verificação
     *     apontando para o domínio dele
     */
    public record AppProps(@NotBlank String baseUrl) {}

    /**
     * @param from {@code MAIL_FROM} de integrations.md §12: remetente das mensagens transacionais
     * @param provider provedor ativo em {@code staging} e {@code prod}. Em {@code local} e {@code
     *     test} o {@code LoggingMailAdapter} atende a porta e este valor é ignorado (BR-203)
     * @param resendApiKey {@code RESEND_API_KEY}. Obrigatória apenas quando {@code provider =
     *     RESEND}; ART-083 exige que venha de variável de ambiente, nunca de arquivo versionado
     */
    public record MailProps(
            @NotBlank String from, @NotNull MailProvider provider, String resendApiKey) {

        /** Adapters disponíveis para {@link com.devtime.shared.mail.MailPort}. */
        public enum MailProvider {
            SMTP,
            RESEND
        }

        /**
         * CF-03: a incoerência entre provedor e credencial falha na inicialização, e não no
         * primeiro envio. Descobrir a chave ausente pelo e-mail de verificação que nunca chegou é o
         * modo de falha caro.
         */
        @AssertTrue(
                message =
                        "defina a variável de ambiente RESEND_API_KEY quando"
                                + " MAIL_PROVIDER=resend (a chave começa com re_)")
        public boolean isResendApiKeyPresentWhenResendSelected() {
            return provider != MailProvider.RESEND
                    || (resendApiKey != null && !resendApiKey.isBlank());
        }
    }

    /**
     * @param issuer claim {@code iss}, validada na verificação do token (security.md §5.2)
     * @param audience claim {@code aud}, validada na verificação do token
     */
    public record ApiProps(@NotBlank String issuer, @NotBlank String audience) {}

    /**
     * @param allowedOrigins origens permitidas por ambiente (security.md §8.3). Nunca {@code "*"}:
     *     o cookie de refresh exige {@code allowCredentials = true}, e o par curinga + credenciais
     *     é rejeitado pelo navegador e pela especificação de CORS.
     */
    public record CorsProps(@NotEmpty List<@NotBlank String> allowedOrigins) {}

    /**
     * @param jwtSecret segredo HMAC. TK-01 exige no mínimo 256 bits; ART-083 exige que venha de
     *     variável de ambiente, nunca de arquivo versionado
     * @param accessTokenTtl TK-02 / ART-080: 15 minutos
     * @param refreshTokenTtl ART-080: 30 dias
     * @param bcryptStrength ART-081 / PW-01: custo 12
     * @param clockSkew CE-S-08: tolerância na validação de {@code exp}/{@code iat}
     */
    public record SecurityProps(
            // ER-04: a mensagem descreve o problema e o contexto. Sem ela, o texto padrão do Bean
            // Validation ("tamanho deve ser entre 32 e ...") não indica de onde o valor deveria
            // vir.
            @NotBlank(
                            message =
                                    "defina a variável de ambiente DEVTIME_JWT_SECRET"
                                            + " (copie .env.example para .env)")
                    @Size(
                            min = 32,
                            message =
                                    "DEVTIME_JWT_SECRET precisa de no mínimo {min} caracteres"
                                            + " (TK-01 exige 256 bits). Gere com:"
                                            + " openssl rand -base64 48")
                    String jwtSecret,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            @Min(4) @Max(15) int bcryptStrength,
            @NotNull Duration clockSkew) {}
}
