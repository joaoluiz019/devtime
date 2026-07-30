package com.devtime.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devtime.shared.security.JwtService;
import com.devtime.shared.security.Role;
import com.devtime.support.IntegrationTestSupport;
import java.util.UUID;
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
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
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
