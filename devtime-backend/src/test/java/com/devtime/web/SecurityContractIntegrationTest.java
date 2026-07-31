package com.devtime.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devtime.shared.security.JwtService;
import com.devtime.shared.security.Role;
import com.devtime.support.IntegrationTestSupport;
import com.devtime.support.ProbeController;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato da borda HTTP: allowlist pública, propagação de tenant e formato de erro.
 *
 * <p>Cobre CA-02 de security.md (nenhum endpoint fora da allowlist acessível sem autenticação),
 * TI-01 (o {@code tenantId} vem só do token) e ART-072 (todo erro em RFC 7807).
 */
@AutoConfigureMockMvc
@Import(ProbeController.class)
class SecurityContractIntegrationTest extends IntegrationTestSupport {

    private static final String PROBE = "/api/v1/test-probe";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private com.devtime.support.SessionFixture sessionFixture;

    private UUID userId;
    private UUID tenantId;
    private UUID membershipId;

    /**
     * Organização, usuário e vínculo reais.
     *
     * <p>Identificadores sintéticos deixaram de bastar com T-001-14: o filtro passou a verificar
     * situação da organização e do vínculo a cada requisição (permissions.md §4.1, passos 3 e 4), e
     * um token que aponta para um vínculo inexistente é recusado — como deve ser.
     */
    @org.junit.jupiter.api.BeforeEach
    void createSession() {
        var session = sessionFixture.create(Role.OWNER);
        tenantId = session.tenantId();
        userId = session.userId();
        membershipId = session.membershipId();
    }

    @Test
    @DisplayName("CA-02: endpoint fora da allowlist é negado sem autenticação")
    void protectedEndpointMustRequireAuthentication() throws Exception {
        mockMvc.perform(get(PROBE + "/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DEVTIME-1001"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("ART-085: token inválido é negado com a mesma resposta de token ausente")
    void invalidTokenMustBeIndistinguishableFromMissingToken() throws Exception {
        mockMvc.perform(get(PROBE + "/session").header("Authorization", "Bearer nao-e-um-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVTIME-1001"));
    }

    @Test
    @DisplayName("security.md §7.1: o health check é público")
    void healthEndpointMustBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TK-03: o TenantContextFilter deriva as permissões do papel a cada requisição")
    void tenantContextMustBePopulatedFromTokenClaims() throws Exception {
        mockMvc.perform(get(PROBE + "/session").header("Authorization", bearer(Role.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                // As permissões não vêm do token: são resolvidas de RolePermissions.
                .andExpect(jsonPath("$.permissionCount").value(greaterThan(0)));
    }

    @Test
    @DisplayName("TI-01 / ART-021: um tenantId enviado na query é ignorado")
    void requestProvidedTenantIdMustBeIgnored() throws Exception {
        UUID attackerTenantId = UUID.randomUUID();

        mockMvc.perform(
                        get(PROBE + "/session-ignoring-request-tenant")
                                .param("tenantId", attackerTenantId.toString())
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTenantId").value(tenantId.toString()));
    }

    @Test
    @DisplayName("ART-024: recurso não encontrado retorna 404 DEVTIME-2002 em RFC 7807")
    void notFoundMustFollowProblemDetails() throws Exception {
        mockMvc.perform(get(PROBE + "/not-found").header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DEVTIME-2002"))
                .andExpect(jsonPath("$.type").value(containsString("resource-not-found")))
                .andExpect(jsonPath("$.title").value("Não encontrado"))
                .andExpect(jsonPath("$.instance").value(PROBE + "/not-found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("RN-004: conflito de versão retorna 409 DEVTIME-2004 com o contexto do conflito")
    void versionConflictMustReturnConflict() throws Exception {
        mockMvc.perform(
                        get(PROBE + "/version-conflict")
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVTIME-2004"))
                .andExpect(jsonPath("$.entityType").value("Membership"))
                .andExpect(jsonPath("$.expectedVersion").value(3));
    }

    @Test
    @DisplayName("EX-04: exceção não mapeada vira 500 DEVTIME-9001 sem vazar detalhe interno")
    void unexpectedExceptionMustNotLeakDetails() throws Exception {
        mockMvc.perform(get(PROBE + "/unexpected").header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DEVTIME-9001"))
                .andExpect(jsonPath("$.detail").value(not(containsString("detalhe interno"))))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("ART-072: erro de validação retorna 400 DEVTIME-2000 com errors[] campo a campo")
    void validationErrorMustListFields() throws Exception {
        mockMvc.perform(
                        post(PROBE + "/validated")
                                .header("Authorization", bearer(Role.OWNER))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVTIME-2000"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value(not(emptyString())));
    }

    @Test
    @DisplayName("security.md §8.2: os cabeçalhos de segurança estão presentes")
    void securityHeadersMustBePresent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(
                        header().string(
                                        "Permissions-Policy",
                                        "geolocation=(), camera=(), microphone=()"))
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        containsString("default-src 'self'")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("ER-07: toda resposta carrega o traceId também em cabeçalho, para o suporte")
    void everyResponseMustCarryTraceId() throws Exception {
        mockMvc.perform(get(PROBE + "/session").header("Authorization", bearer(Role.OWNER)))
                .andExpect(header().string("X-Trace-Id", not(emptyString())));
    }

    private String bearer(Role role) {
        return "Bearer "
                + jwtService.issueAccessToken(
                        userId, tenantId, membershipId, role, "America/Sao_Paulo");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.csrf();
    }
}
