package com.devtime.auth;

import com.devtime.auth.AuthService.RequestMetadata;
import com.devtime.auth.AuthService.SessionOutcome;
import com.devtime.auth.domain.AuthExceptions;
import com.devtime.auth.dto.AuthRequests.ChangePasswordRequest;
import com.devtime.auth.dto.AuthRequests.ForgotPasswordRequest;
import com.devtime.auth.dto.AuthRequests.LoginRequest;
import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.auth.dto.AuthRequests.ResendVerificationRequest;
import com.devtime.auth.dto.AuthRequests.ResetPasswordRequest;
import com.devtime.auth.dto.AuthRequests.SelectTenantRequest;
import com.devtime.auth.dto.AuthRequests.VerifyEmailRequest;
import com.devtime.auth.dto.AuthResponses.MeResponse;
import com.devtime.auth.dto.AuthResponses.MessageResponse;
import com.devtime.auth.dto.AuthResponses.RegisterResponse;
import com.devtime.auth.dto.AuthResponses.SessionResponse;
import com.devtime.auth.dto.AuthResponses.TenantOptionResponse;
import com.devtime.shared.ratelimit.RateLimitPolicy;
import com.devtime.shared.ratelimit.RateLimiter;
import com.devtime.shared.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticação e sessão ({@code authentication.md} §5).
 *
 * <p>BR-080: nenhuma regra de negócio aqui. O controller traduz HTTP — cookie, IP, user agent —
 * para o serviço e de volta. CA-02: o refresh token só sai em {@code Set-Cookie}, nunca no corpo.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Cadastro, sessão, senha e seleção de organização")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshCookieFactory cookieFactory;
    private final RateLimiter rateLimiter;
    private final TenantContext tenantContext;

    @PostMapping("/register")
    @Operation(
            summary = "Cria conta e organização",
            description =
                    "RN-451 (política de senha), RN-452 (e-mail único), RN-501 (9 categorias"
                            + " padrão) e INV-TEN-02 (OWNER ativo), tudo em uma transação."
                            + " Rate limit de 5/hora por IP.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Conta criada; verificação enviada"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2452 — e-mail já cadastrado"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2451 — senha fora da política"),
        @ApiResponse(responseCode = "429", description = "Rate limit; header Retry-After")
    })
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimitPolicy.REGISTER, clientIp(httpRequest)); // SG-14
        RegisterResponse created = authService.register(request, metadataOf(httpRequest));
        // BR-088: 201 sempre com Location. Aponta para a sessão corrente, que é o recurso que o
        // cliente poderá ler assim que verificar o e-mail.
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).body(created);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Autentica",
            description =
                    "Ordem normativa da §6.1 do spec. Um único tenant devolve sessão pronta;"
                            + " dois ou mais devolvem token de pré-seleção. Rate limit de 10/min"
                            + " por IP + e-mail.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Autenticado; cookie de refresh definido"),
        @ApiResponse(responseCode = "401", description = "DEVTIME-1001 — credenciais inválidas"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1008 ou DEVTIME-1003"),
        @ApiResponse(responseCode = "423", description = "DEVTIME-1006 — conta bloqueada")
    })
    public ResponseEntity<SessionResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // AU-04: o limite é por IP + e-mail. Só por IP puniria uma rede corporativa inteira; só por
        // e-mail permitiria distribuir o ataque entre máquinas.
        rateLimiter.consume(RateLimitPolicy.LOGIN, clientIp(httpRequest) + "|" + request.email());
        return withRefreshCookie(authService.login(request, metadataOf(httpRequest)));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renova a sessão",
            description =
                    "RT-03: rotaciona o token. RN-005: reuso de token rotacionado revoga toda a"
                            + " cadeia e devolve DEVTIME-1005.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Renovada; novo cookie definido"),
        @ApiResponse(responseCode = "401", description = "DEVTIME-1004 ou DEVTIME-1005")
    })
    public ResponseEntity<SessionResponse> refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String cookie,
            HttpServletRequest httpRequest) {
        requireRefreshCookie(cookie);
        return withRefreshCookie(authService.refresh(cookie, metadataOf(httpRequest)));
    }

    @PostMapping("/verify-email")
    @Operation(
            summary = "Verifica o e-mail",
            description =
                    "Idempotente (CA-08): o clique repetido no mesmo link responde sucesso, porque"
                            + " clientes de e-mail com pré-visualização consomem o link antes do"
                            + " usuário. Ativa os convites pendentes.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verificado; sessão emitida"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-1010 — token inválido"),
        @ApiResponse(responseCode = "410", description = "DEVTIME-1009 — token expirado")
    })
    public ResponseEntity<SessionResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request, HttpServletRequest httpRequest) {
        return withRefreshCookie(authService.verifyEmail(request.token(), metadataOf(httpRequest)));
    }

    @PostMapping("/resend-verification")
    @Operation(
            summary = "Reenvia a verificação",
            description =
                    "RN-457: o reenvio invalida o token anterior. SG-01: a resposta é idêntica com"
                            + " e sem conta correspondente.")
    @ApiResponse(responseCode = "202", description = "Solicitação aceita")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        rateLimiter.consume(RateLimitPolicy.RESEND_VERIFICATION, request.email());
        authService.resendVerification(request.email());
        return ResponseEntity.accepted()
                .body(
                        new MessageResponse(
                                "Se o e-mail estiver cadastrado e pendente de verificação, você"
                                        + " receberá as instruções em instantes."));
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerra a sessão corrente", description = "RT-05.")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada; cookie removido")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String cookie) {
        authService.logout(cookie);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Encerra todas as sessões", description = "FA-12: inclusive a corrente.")
    @ApiResponse(responseCode = "204", description = "Sessões encerradas")
    public ResponseEntity<Void> logoutAll() {
        authService.logoutAll(tenantContext.requireUserId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @GetMapping("/tenants")
    @Operation(
            summary = "Lista as organizações do usuário",
            description = "CX-08: organizações suspensas aparecem marcadas, não omitidas.")
    @ApiResponse(responseCode = "200", description = "Organizações disponíveis")
    public List<TenantOptionResponse> listTenants() {
        return authService.listTenants(tenantContext.requireUserId());
    }

    @PostMapping("/select-tenant")
    @Operation(
            summary = "Seleciona a organização da sessão",
            description = "RN-459: exige vínculo ACTIVE. Também usado para trocar de organização.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Selecionada; token com claim tid"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1102 ou DEVTIME-1202"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — inexistente ou alheia")
    })
    public SessionResponse selectTenant(
            @Valid @RequestBody SelectTenantRequest request,
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String cookie) {
        // Sem novo cookie: a seleção não rotaciona o refresh, apenas o vincula à organização
        // escolhida. Rotacionar aqui produziria duas rotações por login em contas multi-tenant.
        return authService.selectTenant(request, tenantContext.requireUserId(), cookie).response();
    }

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Solicita redefinição de senha",
            description = "PW-07 / SG-02: sempre 202, com ou sem conta correspondente.")
    @ApiResponse(responseCode = "202", description = "Solicitação aceita")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        rateLimiter.consume(RateLimitPolicy.PASSWORD_RESET, request.email());
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted()
                .body(
                        new MessageResponse(
                                "Se o e-mail estiver cadastrado, você receberá as instruções em"
                                        + " instantes."));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Redefine a senha",
            description =
                    "RN-461: token de 1 hora, uso único. Revoga todas as sessões (CE-AU-05) e"
                            + " desbloqueia a conta (CX-07).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Senha redefinida"),
        @ApiResponse(responseCode = "410", description = "DEVTIME-1007 — token expirado ou usado"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2451 — senha fora da política")
    })
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .body(new MessageResponse("Senha redefinida com sucesso."));
    }

    @PostMapping("/change-password")
    @Operation(
            summary = "Altera a própria senha",
            description =
                    "RN-454: revoga todas as sessões exceto a corrente. PW-05: exige a atual.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Senha alterada"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-1011, DEVTIME-1012 ou DEVTIME-2451")
    })
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String cookie) {
        authService.changePassword(request, tenantContext.requireUserId(), cookie);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(
            summary = "Dados da sessão corrente",
            description =
                    "CA-05: usuário, organização, papel e permissões em uma única chamada. As"
                            + " permissões são derivadas do papel a cada requisição (TK-03).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sessão corrente"),
        @ApiResponse(
                responseCode = "401",
                description = "DEVTIME-1002 — organização não selecionada")
    })
    public MeResponse me() {
        return authService.me(tenantContext.requireUserId(), tenantContext.requireTenantId());
    }

    /**
     * Anexa o cookie rotacionado à resposta.
     *
     * <p>CA-02 / CP-02: é o único lugar do sistema por onde o valor bruto do refresh token sai — e
     * sai exclusivamente em {@code Set-Cookie}.
     */
    private ResponseEntity<SessionResponse> withRefreshCookie(SessionOutcome outcome) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieFactory.create(outcome.rawRefreshToken()).toString())
                .body(outcome.response());
    }

    private void requireRefreshCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            throw AuthExceptions.refreshTokenInvalid(); // DEVTIME-1004
        }
    }

    private RequestMetadata metadataOf(HttpServletRequest request) {
        return new RequestMetadata(request.getHeader(HttpHeaders.USER_AGENT), clientIp(request));
    }

    /**
     * Endereço de origem da conexão.
     *
     * <p>{@code getRemoteAddr()} e <b>não</b> {@code X-Forwarded-For}: TS-001-20 exige que headers
     * informados pelo cliente não contornem o limite, e o valor confiável de proxy reverso é
     * responsabilidade da borda, que o repassa por {@code RemoteIpValve} — mecanismo que substitui
     * o próprio {@code getRemoteAddr()}. Ler o header diretamente aqui permitiria que qualquer
     * cliente escolhesse o próprio identificador de limite.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
