package com.devtime.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devtime.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contrato HTTP da feature 001 (TS-001-16, TS-001-17, TS-001-19, AC-001-37).
 *
 * <p>Verifica o que só é observável na borda: atributos do cookie, formato de erro, ausência de
 * campo sensível na serialização e os códigos exatos de {@code authentication.md} §8.
 */
@AutoConfigureMockMvc
class AuthApiIntegrationTest extends IntegrationTestSupport {

    private static final String BASE = "/api/v1/auth";

    /** Campos que jamais podem aparecer em resposta (INV-USR-02, CP-01, CP-02, BR-108). */
    private static final List<String> FORBIDDEN_FIELDS =
            List.of("passwordhash", "password", "tokenhash", "refreshtoken");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.devtime.shared.security.JwtService jwtService;

    @Test
    @DisplayName("§5.2: o cadastro devolve 201 com Location e sem nenhum campo sensível")
    void registerMustReturnCreatedWithoutSensitiveFields() throws Exception {
        MvcResult result =
                mockMvc.perform(registerRequest("api-registro"))
                        .andExpect(status().isCreated())
                        .andExpect(header().exists("Location"))
                        .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
                        .andExpect(jsonPath("$.userId").isNotEmpty())
                        .andExpect(jsonPath("$.tenantId").isNotEmpty())
                        .andReturn();

        assertNoSensitiveField(result);
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .as("CP-08: o cadastro não abre sessão")
                .isNull();
    }

    @Test
    @DisplayName("TS-001-19: o cookie de refresh é HttpOnly, Secure, SameSite=Strict e restrito")
    void refreshCookieMustCarrySecurityAttributes() throws Exception {
        String email = register("api-cookie");

        MvcResult login =
                mockMvc.perform(
                                post(BASE + "/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json(
                                                        Map.of(
                                                                "email",
                                                                email,
                                                                "password",
                                                                "SenhaForte123"))))
                        .andExpect(status().isOk())
                        .andReturn();

        String cookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(cookie)
                .isNotNull()
                .startsWith("dt_refresh=")
                .contains("HttpOnly") // SG-06
                .contains("Secure")
                .contains("SameSite=Strict") // SG-07
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=2592000"); // 30 dias — ART-080
        assertNoSensitiveField(login);
    }

    @Test
    @DisplayName("CA-02: o refresh token nunca aparece no corpo da resposta de login")
    void refreshTokenMustNeverAppearInBody() throws Exception {
        String email = register("api-corpo");

        MvcResult login =
                mockMvc.perform(
                                post(BASE + "/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json(
                                                        Map.of(
                                                                "email",
                                                                email,
                                                                "password",
                                                                "SenhaForte123"))))
                        .andReturn();

        String rawCookieValue =
                login.getResponse()
                        .getHeader("Set-Cookie")
                        .split(";")[0]
                        .substring("dt_refresh=".length());
        assertThat(login.getResponse().getContentAsString())
                .as("o valor do cookie não pode ter contrapartida no JSON")
                .doesNotContain(rawCookieValue);
    }

    @Test
    @DisplayName("§8: credenciais inválidas devolvem 401 DEVTIME-1001 em RFC 7807")
    void invalidCredentialsMustReturnProblemDetail() throws Exception {
        mockMvc.perform(
                        post(BASE + "/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                Map.of(
                                                        "email",
                                                        "inexistente-"
                                                                + UUID.randomUUID()
                                                                + "@exemplo.com",
                                                        "password",
                                                        "SenhaForte123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVTIME-1001"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(BASE + "/login"));
    }

    @Test
    @DisplayName("AC-001-23: termos não aceitos devolvem 400 com o campo em errors[]")
    void unacceptedTermsMustReturnBadRequest() throws Exception {
        mockMvc.perform(
                        post(BASE + "/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(registerBody("api-termos", false, "SenhaForte123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVTIME-2000"))
                .andExpect(
                        jsonPath("$.errors[*].field")
                                .value(org.hamcrest.Matchers.hasItem("acceptedTerms")));
    }

    @Test
    @DisplayName("AC-001-13: senha fora da política devolve 422 DEVTIME-2451 sem ecoar a senha")
    void weakPasswordMustReturnUnprocessable() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(BASE + "/register")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json(
                                                        registerBody(
                                                                "api-senha", true, "senhafraca1"))))
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.code").value("DEVTIME-2451"))
                        .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("senhafraca1");
    }

    @Test
    @DisplayName("SG-02: 'esqueci a senha' devolve 202 idêntico para conta existente e inexistente")
    void forgotPasswordMustAlwaysReturnAccepted() throws Exception {
        String existing = register("api-esqueci");

        String withAccount = forgotPasswordBody(existing);
        String withoutAccount =
                forgotPasswordBody("naoexiste-" + UUID.randomUUID() + "@exemplo.com");

        assertThat(withAccount)
                .as("AC-001-32: os corpos precisam ser idênticos exceto pelo traceId")
                .isEqualTo(withoutAccount);
    }

    @Test
    @DisplayName("AC-001-21/CE-P-11: token de pré-seleção devolve 401 DEVTIME-1002 no negócio")
    void preSelectionTokenMustNotReachBusinessEndpoints() throws Exception {
        String preAuthToken = jwtService.issuePreAuthToken(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + preAuthToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVTIME-1002"));

        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVTIME-1001"));
    }

    @Test
    @DisplayName("§5.11: sessões e /me exigem autenticação")
    void sessionEndpointsMustRequireAuthentication() throws Exception {
        mockMvc.perform(get(BASE + "/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/sessions")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-001-39/ART-073: o sexto cadastro do mesmo IP em uma hora devolve 429")
    void registerMustBeRateLimitedPerIp() throws Exception {
        String ip = "198.51.100." + (int) (System.nanoTime() % 200 + 20);
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(registerRequest("api-limite-" + attempt).with(remoteAddress(ip)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(registerRequest("api-limite-6").with(remoteAddress(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("DEVTIME-9002"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddress(
            String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            registerRequest(String prefix) throws Exception {
        return post(BASE + "/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(registerBody(prefix, true, "SenhaForte123")));
    }

    private String register(String prefix) throws Exception {
        Map<String, Object> body = registerBody(prefix, true, "SenhaForte123");
        mockMvc.perform(
                        post(BASE + "/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(body)))
                .andExpect(status().isCreated());
        String email = (String) body.get("email");
        activate(email);
        return email;
    }

    /**
     * Ativa a conta pelo caminho real de verificação.
     *
     * <p>Usa o token capturado do evento, e não uma escrita direta no banco: é o mesmo percurso que
     * o usuário faria a partir do e-mail.
     */
    private void activate(String email) throws Exception {
        String token = com.devtime.auth.CapturedAuthEvents.tokenForEmail(email);
        mockMvc.perform(
                        post(BASE + "/verify-email")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("token", token))))
                .andExpect(status().isOk());
    }

    private String forgotPasswordBody(String email) throws Exception {
        return mockMvc.perform(
                        post(BASE + "/forgot-password")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("email", email))))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Map<String, Object> registerBody(
            String prefix, boolean acceptedTerms, String password) {
        return Map.of(
                "email",
                prefix + "-" + UUID.randomUUID() + "@exemplo.com",
                "password",
                password,
                "fullName",
                "Rafael Mendes",
                "tenantName",
                "Rafael Mendes Dev",
                "timezone",
                "America/Sao_Paulo",
                "acceptedTerms",
                acceptedTerms);
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private void assertNoSensitiveField(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString().toLowerCase(Locale.ROOT);
        FORBIDDEN_FIELDS.forEach(
                field ->
                        assertThat(body)
                                .as("INV-USR-02 / CP-01: %s não pode aparecer em resposta", field)
                                .doesNotContain("\"" + field + "\""));
    }
}
