package com.devtime.web;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devtime.shared.security.JwtService;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do próprio perfil (users.md §5, P26 e P27).
 *
 * <p>É a borda que o serviço sozinho não prova: filtro de tenant, CSRF, autorização de método e
 * serialização. Toda pessoa autenticada edita o próprio cadastro — <b>qualquer</b> papel, inclusive
 * {@code MEMBER}, porque §16 exige apenas requisição autenticada e o ownership é estrutural (a
 * operação age sobre o usuário da sessão, não sobre um identificador do corpo).
 */
@AutoConfigureMockMvc
class UserProfileApiIntegrationTest extends FeatureTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @Test
    @DisplayName("§5.1: GET /users/me devolve o perfil do usuário autenticado")
    void getProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userAId.toString()))
                .andExpect(jsonPath("$.email").value(not(emptyString())));
    }

    @Test
    @DisplayName("§5.1: PATCH /users/me altera o próprio nome")
    void patchProfile() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\":\"Nome Alterado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nome Alterado"));
    }

    @Test
    @DisplayName("§5.2: PATCH /users/me/preferences altera as próprias preferências")
    void patchPreferences() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/users/me/preferences")
                                .with(csrf())
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.theme").value("DARK"));
    }

    @Test
    @DisplayName("§16: MEMBER edita o próprio perfil — a permissão não é de papel, é de dono")
    void memberCanEditOwnProfile() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .with(csrf())
                                .header("Authorization", bearer(Role.MEMBER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\":\"Membro Alterado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Membro Alterado"));
    }

    @Test
    @DisplayName("Sem token CSRF a resposta é DEVTIME-1105, e não a de falta de permissão")
    void mutationWithoutCsrfIsRejectedWithItsOwnCode() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .header("Authorization", bearer(Role.OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\":\"Sem CSRF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        // "Você não tem permissão" mandaria procurar um
                                        // administrador que não tem como resolver um cookie.
                                        "DEVTIME-1105"));
    }

    private String bearer(Role role) {
        return "Bearer "
                + jwtService.issueAccessToken(
                        userAId, tenantAId, UUID.randomUUID(), role, "America/Sao_Paulo");
    }
}
