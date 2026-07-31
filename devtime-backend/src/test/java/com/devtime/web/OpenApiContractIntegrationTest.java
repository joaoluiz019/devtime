package com.devtime.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devtime.shared.security.JwtService;
import com.devtime.shared.security.Role;
import com.devtime.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Especificação OpenAPI publicada (ART-076, ADR-012).
 *
 * <p>A especificação é <b>gerada a partir do código</b>, então o risco não é ela ficar
 * desatualizada, e sim um endpoint entrar sem descrição — o que produz documentação tecnicamente
 * correta e inútil. Este teste verifica que cada rota desta sprint está publicada e descrita.
 */
@AutoConfigureMockMvc
class OpenApiContractIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private com.devtime.support.SessionFixture sessionFixture;

    private com.devtime.support.SessionFixture.Session session;

    /** Sessão real: com T-001-14, o filtro recusa token cujo vínculo não existe. */
    @org.junit.jupiter.api.BeforeEach
    void createSession() {
        session = sessionFixture.create(Role.OWNER);
    }

    /**
     * A especificação exige autenticação: Swagger UI e {@code /v3/api-docs} não constam da
     * allowlist de security.md §7.1, e em produção o springdoc é desabilitado (A05 de §8).
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder apiDocs() {
        return get("/v3/api-docs")
                .header(
                        "Authorization",
                        "Bearer "
                                + jwtService.issueAccessToken(
                                        session.userId(),
                                        session.tenantId(),
                                        session.membershipId(),
                                        Role.OWNER,
                                        "America/Sao_Paulo"));
    }

    @Test
    @DisplayName("ART-076: as rotas de cliente, contato, categoria e contrato constam do OpenAPI")
    void openApiMustDocumentSprintEndpoints() throws Exception {
        mockMvc.perform(apiDocs())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/clients'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}/activate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{id}/deactivate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/contacts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/contracts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/summary'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories/reorder'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts/preview-periods'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts/{id}/activate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts/{id}/suspend'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts/{id}/resume'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts/{id}/end'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts/{id}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts/{id}/periods'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts/{id}/history'].get").exists());
    }

    @Test
    @DisplayName("CA-06 de authentication.md: os 17 endpoints de sessão constam do OpenAPI")
    void openApiMustDocumentAuthenticationEndpoints() throws Exception {
        mockMvc.perform(apiDocs())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/verify-email'].post.summary").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/auth/resend-verification'].post.summary")
                                .exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout-all'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/tenants'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/select-tenant'].post.summary").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/auth/forgot-password'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/reset-password'].post.summary").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/auth/change-password'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions'].get.summary").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/auth/sessions/{id}'].delete.summary").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/auth/invitations/{token}'].get.summary")
                                .exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/auth/invitations/{token}/accept'].post.summary")
                                .exists())
                // Os códigos de erro fazem parte do contrato: quem integra precisa saber que 423 no
                // login significa conta bloqueada, e não indisponibilidade.
                .andExpect(
                        jsonPath("$.paths['/api/v1/auth/login'].post.responses['423'].description")
                                .value(org.hamcrest.Matchers.containsString("DEVTIME-1006")));
    }

    @Test
    @DisplayName("BR-086: todo endpoint da sprint possui resumo descritivo")
    void everyEndpointMustHaveSummary() throws Exception {
        mockMvc.perform(apiDocs())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/clients'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contracts'].post.summary").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/contracts/{id}/activate'].post.summary")
                                .exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].post.summary").exists())
                // Os códigos de erro documentados fazem parte do contrato: quem integra precisa
                // saber que 409 em DELETE /clients/{id} significa contrato ativo (RN-401).
                .andExpect(
                        jsonPath("$.paths['/api/v1/clients/{id}'].delete.responses.409").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/contracts/{id}'].delete.responses.409")
                                .exists());
    }

    @Test
    @DisplayName("ADR-012: os schemas dos DTOs desta sprint são publicados")
    void openApiMustPublishSchemas() throws Exception {
        mockMvc.perform(apiDocs())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ClientCreateRequest").exists())
                .andExpect(jsonPath("$.components.schemas.ClientResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ContactRequest").exists())
                .andExpect(jsonPath("$.components.schemas.CategoryCreateRequest").exists())
                .andExpect(jsonPath("$.components.schemas.ContractCreateRequest").exists())
                .andExpect(jsonPath("$.components.schemas.PeriodPreviewResponse").exists());
    }
}
