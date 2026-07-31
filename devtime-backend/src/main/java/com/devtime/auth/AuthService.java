package com.devtime.auth;

import com.devtime.auth.dto.AuthRequests.ChangePasswordRequest;
import com.devtime.auth.dto.AuthRequests.LoginRequest;
import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.auth.dto.AuthRequests.SelectTenantRequest;
import com.devtime.auth.dto.AuthResponses.MeResponse;
import com.devtime.auth.dto.AuthResponses.RegisterResponse;
import com.devtime.auth.dto.AuthResponses.SessionResponse;
import com.devtime.auth.dto.AuthResponses.TenantOptionResponse;
import java.util.List;
import java.util.UUID;

/**
 * Cadastro, autenticação, sessão e senha (spec 001 §22.2).
 *
 * <p>É o <b>único</b> ponto por onde {@code userId}, {@code tenantId} e {@code role} entram no
 * sistema (spec §2). Toda consulta subsequente depende do {@code TenantContext} produzido aqui.
 *
 * <p>BR-069: nenhum método conhece {@code HttpServletRequest}, cookie ou {@code ResponseEntity}. O
 * que é HTTP entra como {@link RequestMetadata} e sai como {@link SessionOutcome}; a tradução para
 * cookie é do controller.
 */
public interface AuthService {

    /**
     * Dados da requisição usados na sessão (AU-07: IP e user agent por sessão).
     *
     * @param ipAddress endereço de origem, obtido da conexão — nunca de header informado pelo
     *     cliente, que seria trivialmente forjável
     */
    record RequestMetadata(String userAgent, String ipAddress) {}

    /**
     * Sessão emitida.
     *
     * @param rawRefreshToken valor bruto destinado ao cookie {@code HttpOnly}; CA-02 de {@code
     *     authentication.md} proíbe que ele apareça no corpo da resposta
     */
    record SessionOutcome(SessionResponse response, String rawRefreshToken) {}

    /** {@code POST /auth/register} — RN-451, RN-452, RN-501, INV-TEN-02 (§5.2). */
    RegisterResponse register(RegisterRequest request, RequestMetadata metadata);

    /** {@code POST /auth/login} — ordem de verificação da §6.1, normativa (BR-062). */
    SessionOutcome login(LoginRequest request, RequestMetadata metadata);

    /** {@code POST /auth/refresh} — rotação e detecção de reuso (RT-03, RN-005). */
    SessionOutcome refresh(String rawRefreshToken, RequestMetadata metadata);

    /**
     * {@code POST /auth/verify-email} — §4.2 de state-machines.md.
     *
     * <p>CE-AU-04 / CA-08: idempotente. A segunda chamada com o mesmo link responde sucesso, porque
     * clientes de e-mail com pré-visualização consomem o link antes do usuário.
     */
    SessionOutcome verifyEmail(String rawToken, RequestMetadata metadata);

    /**
     * {@code POST /auth/resend-verification} — RN-457.
     *
     * <p>SG-01: responde igual para e-mail cadastrado e não cadastrado.
     */
    void resendVerification(String email);

    /** {@code GET /auth/tenants} — organizações do usuário (CX-08 inclui as suspensas). */
    List<TenantOptionResponse> listTenants(UUID userId);

    /** {@code POST /auth/select-tenant} — RN-459. */
    SessionOutcome selectTenant(SelectTenantRequest request, UUID userId, String rawRefreshToken);

    /** {@code POST /auth/logout} — RT-05: revoga apenas a sessão corrente. */
    void logout(String rawRefreshToken);

    /** {@code POST /auth/logout-all} — FA-12: revoga todas, inclusive a corrente. */
    void logoutAll(UUID userId);

    /** {@code POST /auth/change-password} — RN-454: mantém apenas a sessão corrente. */
    void changePassword(ChangePasswordRequest request, UUID userId, String rawRefreshToken);

    /** {@code GET /auth/me} — sessão, papel e permissões em uma única chamada (§5.10). */
    MeResponse me(UUID userId, UUID tenantId);
}
