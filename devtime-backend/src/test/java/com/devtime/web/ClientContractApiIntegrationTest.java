package com.devtime.web;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devtime.shared.security.JwtService;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contrato HTTP de clientes, categorias e contratos (clients.md, contracts.md, users.md §8).
 *
 * <p>Exercita o caminho completo — filtro de tenant, autorização, serviço e serialização — para
 * verificar o que só a borda pode provar: {@code Location} no {@code 201} (BR-088), erro em RFC
 * 7807 com código {@code DEVTIME-XXXX} (BR-091) e envelope de paginação (ART-073).
 */
@AutoConfigureMockMvc
class ClientContractApiIntegrationTest extends FeatureTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("BR-088: POST /clients devolve 201 com header Location")
    void createClientShouldReturnLocation() throws Exception {
        mockMvc.perform(
                        post("/api/v1/clients")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Acme Corporation","documentType":"CNPJ",
                                         "documentNumber":"11.222.333/0001-81",
                                         "email":"Financeiro@ACME.com.br"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", not(emptyString())))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.documentNumber").value("11222333000181"))
                .andExpect(jsonPath("$.email").value("financeiro@acme.com.br"))
                .andExpect(jsonPath("$.color").value(not(emptyString())));
    }

    @Test
    @DisplayName("BR-091: documento inválido responde RFC 7807 com DEVTIME-2402 e traceId")
    void invalidDocumentShouldReturnProblemDetail() throws Exception {
        mockMvc.perform(
                        post("/api/v1/clients")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Documento Ruim","documentType":"CPF",
                                         "documentNumber":"111.111.111-11"}
                                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEVTIME-2402"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("ART-073: a listagem devolve o envelope de paginação")
    void listShouldReturnPaginationEnvelope() throws Exception {
        createClient("Cliente Paginado");

        mockMvc.perform(get("/api/v1/clients").header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("RN-012: size acima de 100 é rejeitado com DEVTIME-2006")
    void oversizedPageShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/clients?size=500").header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVTIME-2006"));
    }

    @Test
    @DisplayName("permissions.md §7: VIEWER não cria cliente — 403 DEVTIME-1101")
    void viewerShouldNotCreateClient() throws Exception {
        mockMvc.perform(
                        post("/api/v1/clients")
                                .with(csrf())
                                .header("Authorization", bearer(Role.VIEWER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Proibido\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVTIME-1101"));
    }

    @Test
    @DisplayName("clients.md §9.3: DELETE devolve 204 e o recurso deixa de existir")
    void deleteClientShouldReturnNoContent() throws Exception {
        UUID clientId = createClient("Para Excluir");

        mockMvc.perform(
                        delete("/api/v1/clients/" + clientId)
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/v1/clients/" + clientId)
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVTIME-2002"));
    }

    @Test
    @DisplayName("contracts.md §6: a prévia de períodos não persiste nada")
    void previewPeriodsShouldNotPersist() throws Exception {
        mockMvc.perform(
                        post("/api/v1/contracts/preview-periods")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"type":"MONTHLY_HOURS","monthlyMinutes":2400,
                                         "startDate":"2026-01-10","billingDay":1,
                                         "prorateFirstPeriod":true,"periodsCount":3}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodsPreview.length()").value(3))
                .andExpect(jsonPath("$.periodsPreview[0].contractedMinutes").value(1703))
                .andExpect(jsonPath("$.periodsPreview[0].prorated").value(true))
                .andExpect(jsonPath("$.periodsPreview[0].prorationBasis").value("22 de 31 dias"))
                .andExpect(jsonPath("$.periodsPreview[1].contractedMinutes").value(2400));

        mockMvc.perform(get("/api/v1/contracts").header("Authorization", bearer(Role.OWNER)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("contracts.md §5 e §8.1: criação em DRAFT e ativação com o primeiro período")
    void contractLifecycleShouldFollowDocumentedContract() throws Exception {
        UUID clientId = createClient("Cliente do Contrato");

        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/contracts")
                                        .with(csrf())
                                        .header("Authorization", bearer(Role.OWNER))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"clientId":"%s","name":"Sustentação Mensal",
                                                 "type":"MONTHLY_HOURS","monthlyMinutes":2400,
                                                 "startDate":"2026-01-10","billingDay":1,
                                                 "rolloverPolicy":"NONE","overagePolicy":"WARN"}
                                                """
                                                        .formatted(clientId)))
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Location", not(emptyString())))
                        .andExpect(jsonPath("$.status").value("DRAFT"))
                        .andExpect(jsonPath("$.code").value("CT-0001"))
                        .andExpect(jsonPath("$.availableActions").isArray())
                        .andReturn();

        UUID contractId = idOf(created);

        mockMvc.perform(
                        post("/api/v1/contracts/" + contractId + "/activate")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.firstPeriod.status").value("OPEN"))
                .andExpect(jsonPath("$.firstPeriod.contractedMinutes").value(1703))
                .andExpect(jsonPath("$.firstPeriod.label").value("2026-01"));

        mockMvc.perform(
                        delete("/api/v1/contracts/" + contractId)
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVTIME-2205"));
    }

    @Test
    @DisplayName("users.md §8: categorias listam ordenadas e rejeitam cor inválida")
    void categoryEndpointsShouldFollowContract() throws Exception {
        mockMvc.perform(
                        post("/api/v1/categories")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Consultoria\",\"color\":\"#F97316\","
                                                + "\"icon\":\"pi-comments\",\"billableByDefault\":true}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", not(emptyString())))
                .andExpect(jsonPath("$.isSystem").value(false));

        mockMvc.perform(
                        post("/api/v1/categories")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Cor Ruim\",\"color\":\"laranja\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVTIME-2000"));

        mockMvc.perform(get("/api/v1/categories").header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("clients.md §10: contatos são criados e listados pela rota aninhada")
    void contactEndpointsShouldFollowContract() throws Exception {
        UUID clientId = createClient("Cliente com Contato");

        mockMvc.perform(
                        post("/api/v1/clients/" + clientId + "/contacts")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Marcelo Prado\",\"email\":\"marcelo@acme.com.br\","
                                                + "\"isPrimary\":true,\"receivesReports\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPrimary").value(true));

        mockMvc.perform(
                        get("/api/v1/clients/" + clientId + "/contacts")
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("clients.md §8: o resumo do cliente responde na rota documentada")
    void clientSummaryShouldRespond() throws Exception {
        UUID clientId = createClient("Cliente com Resumo");

        mockMvc.perform(
                        get("/api/v1/clients/" + clientId + "/summary")
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.totals.consumedMinutes").value(0));
    }

    @Test
    @DisplayName("ME-05/SG-05: PATCH não é caminho para alterar o status do contrato")
    void patchShouldNotChangeStatus() throws Exception {
        UUID clientId = createClient("Cliente Patch");
        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/contracts")
                                        .with(csrf())
                                        .header("Authorization", bearer(Role.OWNER))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"clientId":"%s","name":"Contrato","type":"MONTHLY_HOURS",
                                                 "monthlyMinutes":2400,"startDate":"2026-01-10",
                                                 "billingDay":1}
                                                """
                                                        .formatted(clientId)))
                        .andReturn();
        UUID contractId = idOf(created);

        // O campo status não existe no DTO de atualização (ME-05). Com
        // fail-on-unknown-properties = true (application.yml, decisão de F0), enviá-lo é rejeitado
        // na desserialização — barreira mais forte que ignorar silenciosamente.
        mockMvc.perform(
                        patch("/api/v1/contracts/" + contractId)
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Renomeado\",\"status\":\"ACTIVE\",\"version\":0}"))
                .andExpect(status().isBadRequest());

        // A atualização legítima passa e o status permanece DRAFT.
        mockMvc.perform(
                        patch("/api/v1/contracts/" + contractId)
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Renomeado\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renomeado"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ── Apoio ───────────────────────────────────────────────────────────────────────────────

    private UUID createClient(String name) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/clients")
                                        .with(csrf())
                                        .header("Authorization", bearer(Role.OWNER))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"" + name + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        return idOf(result);
    }

    private UUID idOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    /** O token carrega o tenant A criado pela base; o {@code membershipId} é indiferente aqui. */
    private String bearer(Role role) {
        return "Bearer "
                + jwtService.issueAccessToken(
                        userAId, tenantAId, UUID.randomUUID(), role, "America/Sao_Paulo");
    }
}
