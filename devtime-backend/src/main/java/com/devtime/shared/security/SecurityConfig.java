package com.devtime.shared.security;

import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.tenancy.TenantContextFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração de segurança HTTP (security.md §7.1 e §8.2).
 *
 * <p>ART-085: negado por padrão. A allowlist pública abaixo é <b>exaustiva</b> e reproduz a tabela
 * de security.md §7.1 — nenhuma entrada pode ser acrescentada sem ADR.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Endpoints acessíveis sem autenticação (security.md §7.1).
     *
     * <p>Swagger UI e {@code /v3/api-docs} <b>não</b> constam aqui: não estão na allowlist do
     * documento, e acrescentá-los exigiria ADR. Em {@code local}, o acesso é feito com um access
     * token; em {@code prod}, springdoc está desabilitado (A05 de security.md §8).
     */
    private static final String AUTH_BASE = "/api/v1/auth";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TenantContextFilter tenantContextFilter;
    private final ProblemDetailAuthenticationEntryPoint problemDetailHandler;
    private final CsrfCookieFilter csrfCookieFilter;
    private final SpaCsrfTokenRequestHandler spaCsrfTokenRequestHandler;
    private final DevTimeProperties properties;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.cors(Customizer.withDefaults())
                .csrf(
                        csrf ->
                                csrf
                                        // withHttpOnlyFalse: o cliente precisa ler o cookie para
                                        // devolver o token no header (Angular faz isso
                                        // nativamente).
                                        .csrfTokenRepository(
                                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        // Sem isto vale o XorCsrfTokenRequestAttributeHandler
                                        // padrão, que exige o token mascarado no header — mas o
                                        // cookie carrega o valor cru, e a SPA devolve o cru.
                                        .csrfTokenRequestHandler(spaCsrfTokenRequestHandler)
                                        .ignoringRequestMatchers(csrfExemptEndpoints()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.POST, publicPostEndpoints())
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                AUTH_BASE + "/invitations/{token}",
                                                "/actuator/health/**")
                                        .permitAll()
                                        // ART-085: tudo o que não está acima exige autenticação.
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        handling ->
                                handling.authenticationEntryPoint(problemDetailHandler)
                                        .accessDeniedHandler(problemDetailHandler))
                .headers(
                        headers ->
                                headers.contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                contentSecurityPolicy()))
                                        .frameOptions(frame -> frame.deny())
                                        .httpStrictTransportSecurity(
                                                hsts ->
                                                        hsts.includeSubDomains(true)
                                                                .preload(true)
                                                                .maxAgeInSeconds(31_536_000))
                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(
                                                                ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                                        .permissionsPolicyHeader(
                                                permissions ->
                                                        permissions.policy(
                                                                "geolocation=(), camera=(),"
                                                                        + " microphone=()"))
                                        // Respostas de API nunca são cacheadas (security.md §8.2).
                                        .cacheControl(Customizer.withDefaults()))
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class)
                // Depois do CsrfFilter, para que o atributo já esteja no request.
                .addFilterAfter(csrfCookieFilter, CsrfFilter.class)
                .build();
    }

    /**
     * Endpoints POST isentos de CSRF.
     *
     * <p>O critério é único e restrito: a requisição não pode depender de <b>autoridade
     * ambiente</b> — cookie de sessão ou de refresh que o navegador anexaria sozinho em um POST
     * forjado de outro site. Todos os itens abaixo são anteriores a qualquer sessão: o chamador
     * ainda não tem cookie algum, e o efeito é decidido pelo corpo (um token imprevisível ou um
     * e-mail sob limite de taxa), nunca por credencial implícita. Forjar a requisição não dá ao
     * atacante nada que ele já não pudesse fazer chamando o endpoint diretamente.
     *
     * <p>{@code /refresh} e {@code /invitations/*​/accept} <b>não</b> constam aqui, e a omissão é
     * deliberada: o primeiro age exatamente sobre o cookie de refresh, e o segundo considera a
     * sessão quando ela existe (CX-09) — são os dois casos em que o CSRF é a proteção real. Ambos
     * são alcançados por um navegador que já carregou a aplicação, e portanto já recebeu o cookie
     * {@code XSRF-TOKEN} materializado por {@link CsrfCookieFilter}.
     *
     * <p>Sem esta lista, o reenvio da verificação, a redefinição de senha e a confirmação de e-mail
     * respondiam {@code 403} no primeiro clique: o cliente ainda não possuía o cookie de CSRF, e
     * não havia requisição anterior que pudesse tê-lo entregue.
     */
    private String[] csrfExemptEndpoints() {
        return new String[] {
            AUTH_BASE + "/login",
            AUTH_BASE + "/register",
            AUTH_BASE + "/verify-email",
            AUTH_BASE + "/resend-verification",
            AUTH_BASE + "/forgot-password",
            AUTH_BASE + "/reset-password"
        };
    }

    private String[] publicPostEndpoints() {
        return new String[] {
            AUTH_BASE + "/register",
            AUTH_BASE + "/login",
            AUTH_BASE + "/refresh",
            AUTH_BASE + "/verify-email",
            // §5.1 de authentication.md classifica o reenvio como público: quem precisa dele ainda
            // não consegue autenticar, justamente por não ter verificado o e-mail.
            AUTH_BASE + "/resend-verification",
            AUTH_BASE + "/forgot-password",
            AUTH_BASE + "/reset-password",
            // §5.12: "Pública/Autenticada". O aceite parte de um link de e-mail, que pode ser
            // aberto em um navegador sem sessão. A proteção é a imprevisibilidade do token; quando
            // há sessão, o serviço a considera (CX-09).
            AUTH_BASE + "/invitations/*/accept"
        };
    }

    private String contentSecurityPolicy() {
        return "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none'; "
                + "base-uri 'self'; form-action 'self'";
    }

    /**
     * CORS por ambiente (security.md §8.3).
     *
     * <p>{@code allowCredentials} é obrigatório porque o refresh token trafega em cookie. Por isso
     * {@code allowedOrigins} nunca pode ser {@code "*"}: a combinação é rejeitada pela
     * especificação de CORS, e o navegador bloquearia toda requisição autenticada.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Trace-Id", "Location", "Retry-After"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
