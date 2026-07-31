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

import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.shared.security.JwtService;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.TicketScenario;
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
 * Contrato HTTP de etiquetas, tickets e comentários (users.md §9, tickets.md §5 a §10).
 *
 * <p>BR-050 / BR-208: é também a suíte de isolamento dos endpoints novos — todo recurso criado no
 * tenant A precisa responder {@code 404} ao tenant B, por id <b>e</b> por chave legível (ART-024).
 *
 * <p>Exercita o caminho completo (filtro de tenant, autorização, serviço e serialização) para
 * verificar o que só a borda prova: {@code Location} no {@code 201} (BR-088), erro em RFC 7807 com
 * código {@code DEVTIME-XXXX} (BR-091) e a ausência de campos que nunca devem sair do backend.
 */
@AutoConfigureMockMvc
class TicketApiIntegrationTest extends FeatureTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TicketScenario scenario;

    @Test
    @DisplayName("BR-088: POST /tags devolve 201 com Location e o nome normalizado")
    void createTagShouldReturnLocationAndNormalizedName() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tags")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Code Review\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", not(emptyString())))
                .andExpect(jsonPath("$.name").value("code-review"))
                .andExpect(jsonPath("$.usageCount").value(0));
    }

    @Test
    @DisplayName("BR-091: nome de etiqueta duplicado responde RFC 7807 com DEVTIME-2604")
    void duplicateTagShouldReturnProblemDetails() throws Exception {
        createTag("urgente");

        mockMvc.perform(
                        post("/api/v1/tags")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"URGENTE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVTIME-2604"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("BR-088: POST /tickets devolve 201 com Location e a chave derivada do contrato")
    void createTicketShouldReturnLocationAndKey() throws Exception {
        ContractResponse contract = activeContract();

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"contractId":"%s","title":"Corrigir cálculo de frete",
                                         "type":"BUG","priority":"HIGH"}
                                        """
                                                .formatted(contract.id())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", not(emptyString())))
                .andExpect(jsonPath("$.key").value(contract.code() + "-1"))
                .andExpect(jsonPath("$.status").value("BACKLOG"))
                .andExpect(jsonPath("$.spentMinutes").value(0))
                .andExpect(jsonPath("$.availableTransitions").isArray());
    }

    @Test
    @DisplayName("SG-07/SG-08: reporterId e spentMinutes enviados no payload são ignorados")
    void forgedFieldsShouldNotBeAccepted() throws Exception {
        ContractResponse contract = activeContract();

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"contractId":"%s","title":"Com campos forjados",
                                         "reporterId":"%s","spentMinutes":9999}
                                        """
                                                .formatted(contract.id(), userBId)))
                .andExpect(status().isBadRequest()); // fail-on-unknown-properties recusa o payload
    }

    @Test
    @DisplayName("RN-012: size acima de 100 na listagem responde 400 DEVTIME-2006")
    void oversizedPageShouldBeRejected() throws Exception {
        mockMvc.perform(
                        get("/api/v1/tickets")
                                .header("Authorization", bearer(Role.OWNER))
                                .param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVTIME-2006"));
    }

    @Test
    @DisplayName("tickets.md §6.1: GET /tickets/board responde com as colunas do quadro")
    void boardShouldRespond() throws Exception {
        ContractResponse contract = activeContract();
        createTicket(contract, "Ticket do quadro");

        mockMvc.perform(get("/api/v1/tickets/board").header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].status").value("BACKLOG"))
                .andExpect(jsonPath("$.columns[0].totalCount").value(1));
    }

    @Test
    @DisplayName("ME-05/BR-089: a situação muda por POST /transition, com efeito em completedAt")
    void transitionShouldChangeStatus() throws Exception {
        ContractResponse contract = activeContract();
        JsonNode ticket = createTicket(contract, "Ticket a transicionar");

        mockMvc.perform(
                        post("/api/v1/tickets/" + ticket.get("id").asText() + "/transition")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"targetStatus\":\"IN_PROGRESS\",\"version\":%d}"
                                                .formatted(ticket.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").value(not(emptyString())));
    }

    @Test
    @DisplayName("ME-04/EX-09: transição fora da matriz responde 409 com availableTransitions")
    void invalidTransitionShouldListAvailable() throws Exception {
        ContractResponse contract = activeContract();
        JsonNode ticket = createTicket(contract, "Ticket em backlog");

        mockMvc.perform(
                        post("/api/v1/tickets/" + ticket.get("id").asText() + "/transition")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"targetStatus\":\"DONE\",\"version\":%d}"
                                                .formatted(ticket.get("version").asLong())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVTIME-2010"))
                .andExpect(jsonPath("$.availableTransitions").isArray());
    }

    @Test
    @DisplayName("FA-15: GET /tickets/by-key resolve a chave legível")
    void shouldResolveByKey() throws Exception {
        ContractResponse contract = activeContract();
        JsonNode ticket = createTicket(contract, "Ticket buscável");

        mockMvc.perform(
                        get("/api/v1/tickets/by-key/" + ticket.get("key").asText())
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket.get("id").asText()));
    }

    @Test
    @DisplayName("BR-050/ART-024: ticket do tenant A responde 404 ao tenant B, por id e por chave")
    void ticketMustBeIsolatedBetweenTenants() throws Exception {
        ContractResponse contract = activeContract();
        JsonNode ticket = createTicket(contract, "Ticket do tenant A");

        mockMvc.perform(
                        get("/api/v1/tickets/" + ticket.get("id").asText())
                                .header("Authorization", bearerOfB()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVTIME-2002"));

        mockMvc.perform(
                        get("/api/v1/tickets/by-key/" + ticket.get("key").asText())
                                .header("Authorization", bearerOfB()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVTIME-2002"));
    }

    @Test
    @DisplayName("BR-050: comentário do tenant A responde 404 ao tenant B")
    void commentMustBeIsolatedBetweenTenants() throws Exception {
        ContractResponse contract = activeContract();
        JsonNode ticket = createTicket(contract, "Ticket com conversa");
        JsonNode comment = createComment(ticket.get("id").asText(), "Conteúdo do tenant A");

        mockMvc.perform(
                        delete("/api/v1/comments/" + comment.get("id").asText())
                                .with(csrf())
                                .header("Authorization", bearerOfB()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVTIME-2002"));
    }

    @Test
    @DisplayName("BR-050: etiqueta do tenant A responde 404 ao tenant B")
    void tagMustBeIsolatedBetweenTenants() throws Exception {
        JsonNode tag = createTag("urgente");

        mockMvc.perform(
                        delete("/api/v1/tags/" + tag.get("id").asText())
                                .with(csrf())
                                .header("Authorization", bearerOfB()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVTIME-2002"));
    }

    @Test
    @DisplayName(
            "tickets.md §10.1: POST /tickets/{id}/comments devolve 201 com canEdit do servidor")
    void createCommentShouldReturnServerComputedFlags() throws Exception {
        ContractResponse contract = activeContract();
        JsonNode ticket = createTicket(contract, "Ticket comentável");

        mockMvc.perform(
                        post("/api/v1/tickets/" + ticket.get("id").asText() + "/comments")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"Consegui reproduzir\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", not(emptyString())))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.isSystem").value(false));
    }

    @Test
    @DisplayName("RN-811: corpo vazio responde 400 na validação de formato, antes do serviço")
    void blankCommentShouldBeRejected() throws Exception {
        ContractResponse contract = activeContract();
        JsonNode ticket = createTicket(contract, "Ticket comentável");

        mockMvc.perform(
                        post("/api/v1/tickets/" + ticket.get("id").asText() + "/comments")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("RN-812: PATCH /comments/{id} edita e marca editedAt")
    void editCommentShouldMarkEditedAt() throws Exception {
        ContractResponse contract = activeContract();
        JsonNode ticket = createTicket(contract, "Ticket comentável");
        JsonNode comment = createComment(ticket.get("id").asText(), "Versão original");

        mockMvc.perform(
                        patch("/api/v1/comments/" + comment.get("id").asText())
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"body\":\"Versão corrigida\",\"version\":%d}"
                                                .formatted(comment.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Versão corrigida"))
                .andExpect(jsonPath("$.editedAt").value(not(emptyString())));
    }

    @Test
    @DisplayName("§9.1: GET /tickets/{id}/activity devolve a linha do tempo com cursor")
    void activityShouldRespond() throws Exception {
        ContractResponse contract = activeContract();
        JsonNode ticket = createTicket(contract, "Ticket com histórico");

        mockMvc.perform(
                        get("/api/v1/tickets/" + ticket.get("id").asText() + "/activity")
                                .header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("CREATED"));
    }

    @Test
    @DisplayName("CE-P-06: VIEWER não cria ticket — 403 DEVTIME-1101")
    void viewerShouldNotCreateTicket() throws Exception {
        ContractResponse contract = activeContract();

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .with(csrf())
                                .header("Authorization", bearer(Role.VIEWER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"contractId\":\"%s\",\"title\":\"Sem permissão\"}"
                                                .formatted(contract.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVTIME-1101"));
    }

    @Test
    @DisplayName("ART-085: os endpoints novos são negados sem autenticação")
    void newEndpointsMustRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tags")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tickets/board")).andExpect(status().isUnauthorized());
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private ContractResponse activeContract() {
        return asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));
    }

    private JsonNode createTag(String name) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/tags")
                                        .with(csrf())
                                        .header("Authorization", bearer(Role.OWNER))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"%s\"}".formatted(name)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createTicket(ContractResponse contract, String title) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/tickets")
                                        .with(csrf())
                                        .header("Authorization", bearer(Role.OWNER))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"contractId\":\"%s\",\"title\":\"%s\"}"
                                                        .formatted(contract.id(), title)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createComment(String ticketId, String body) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/tickets/" + ticketId + "/comments")
                                        .with(csrf())
                                        .header("Authorization", bearer(Role.OWNER))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"body\":\"%s\"}".formatted(body)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(Role role) {
        return "Bearer "
                + jwtService.issueAccessToken(
                        userAId, tenantAId, UUID.randomUUID(), role, "America/Sao_Paulo");
    }

    private String bearerOfB() {
        return "Bearer "
                + jwtService.issueAccessToken(
                        userBId, tenantBId, UUID.randomUUID(), Role.OWNER, "America/Sao_Paulo");
    }
}
