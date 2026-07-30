package com.devtime.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação OpenAPI 3.1 gerada a partir do código (ART-076, ADR-012).
 *
 * <p>O esquema de segurança é declarado globalmente porque ART-085 nega por padrão: documentar cada
 * endpoint como autenticado individualmente convidaria ao esquecimento, e um endpoint documentado
 * como público mas que não está na allowlist produziria contrato divergente da implementação.
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    private final DevTimeProperties properties;

    @Bean
    OpenAPI devTimeOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("DevTime API")
                                .version("v1")
                                .description(
                                        "API de controle de horas multi-tenant. Erros seguem RFC 7807"
                                                + " com código estável DEVTIME-XXXX.")
                                .license(new License().name("Proprietária")))
                .servers(List.of(new Server().url("/").description("Servidor corrente")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Access token de 15 minutos emitido por "
                                                                + properties.api().issuer()
                                                                + ". O refresh token trafega em"
                                                                + " cookie HttpOnly e não é aceito"
                                                                + " aqui.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
