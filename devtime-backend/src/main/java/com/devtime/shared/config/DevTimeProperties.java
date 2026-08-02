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
 * <p>Os blocos {@code timer} e {@code report} previstos em backend.md §13.2 ainda não existem:
 * pertencem às features 009 e 012. Serão adicionados quando houver quem os leia (CG-10). O bloco
 * {@code storage} — e o de {@code antivirus} — entram com 015-attachments.
 */
@ConfigurationProperties(prefix = "devtime")
@Validated
public record DevTimeProperties(
        @NotNull @Valid ApiProps api,
        @NotNull @Valid CorsProps cors,
        @NotNull @Valid SecurityProps security,
        @NotNull @Valid AppProps app,
        @NotNull @Valid MailProps mail,
        @NotNull @Valid StorageProps storage,
        @NotNull @Valid AntivirusProps antivirus,
        @NotNull @Valid AttachmentProps attachment) {

    /**
     * Object storage (integrations.md §6.2, T-015-01).
     *
     * @param endpoint URL do serviço compatível com S3; MinIO em desenvolvimento
     * @param bucket bucket de destino. SG-01: sempre privado; o acesso é por URL assinada
     * @param region região do SDK. Obrigatória mesmo em MinIO, que a ignora, porque o cliente
     *     recusa iniciar sem ela
     * @param accessKey credencial. ART-083: apenas por variável de ambiente
     * @param secretKey credencial. ART-083: apenas por variável de ambiente
     * @param downloadUrlTtl SG-08: validade curta da URL assinada. Uma URL longeva é um link
     *     público com data marcada — e §19.1 exige que todo acesso seja rastreável a um download
     *     auditado, o que uma URL compartilhada indefinidamente desfaz
     */
    public record StorageProps(
            @NotBlank String endpoint,
            @NotBlank String bucket,
            @NotBlank String region,
            @NotBlank String accessKey,
            @NotBlank String secretKey,
            @NotNull Duration downloadUrlTtl) {}

    /**
     * Verificador antivírus (integrations.md §6.3, T-015-02).
     *
     * <p>RS-03: dependência obrigatória. Sem ela nenhum download é liberado — e é por isso que não
     * existe propriedade para desligá-la. Um interruptor de "pular verificação" seria exatamente o
     * caminho de liberação manual que CP-02 proíbe, apenas com outro nome.
     *
     * @param readTimeout tempo máximo aguardando o veredito. Esgotado, o resultado é {@code
     *     FAILED}, que mantém o download bloqueado (AV-02)
     */
    public record AntivirusProps(
            @NotBlank String host,
            @Min(1) @Max(65535) int port,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {}

    /**
     * Limites de anexo (RN-801, RS-01, RS-10).
     *
     * <p>Configuráveis mas <b>não</b> flexíveis: os valores padrão são os de RN-801, e alterá-los é
     * mudança de regra de negócio, que exige alteração de {@code business-rules.md} antes do código
     * (CG-02). Estão aqui porque OB-08 prevê que a quota passe a vir do plano em F6 — e o ponto de
     * leitura já ser único é o que torna aquela mudança aditiva.
     *
     * @param maxFileSizeBytes RN-801: 10 MB por arquivo
     * @param tenantQuotaBytes RN-801: 1 GB por tenant no plano gratuito
     */
    public record AttachmentProps(@Min(1) long maxFileSizeBytes, @Min(1) long tenantQuotaBytes) {}

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
