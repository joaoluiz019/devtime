package com.devtime.auth;

import com.devtime.auth.dto.AuthResponses.ActiveSessionListResponse;
import com.devtime.shared.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessões ativas do próprio usuário ({@code authentication.md} §5.11).
 *
 * <p>Controller separado de {@link AuthController} por ser um recurso próprio — {@code
 * /auth/sessions} — com ciclo de leitura e exclusão, e não mais uma ação de autenticação.
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessões", description = "Dispositivos com sessão ativa do usuário autenticado")
public class SessionController {

    private final SessionService sessionService;
    private final RefreshTokenService refreshTokenService;
    private final TenantContext tenantContext;

    @GetMapping
    @Operation(
            summary = "Lista as sessões ativas",
            description =
                    "A sessão que originou a requisição vem marcada com current = true. O IP é"
                            + " parcialmente mascarado (§9.2 de security.md).")
    @ApiResponse(responseCode = "200", description = "Sessões ativas do usuário")
    public ActiveSessionListResponse list(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String cookie) {
        UUID currentSessionId = refreshTokenService.identify(cookie).orElse(null);
        return sessionService.listOwn(tenantContext.requireUserId(), currentSessionId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Revoga uma sessão",
            description =
                    "OWN-09: só as próprias sessões. Revogar a sessão corrente equivale a logout."
                            + " Sessão de outro usuário devolve 404, nunca 403 (ART-024).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sessão revogada"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — inexistente ou de outro usuário")
    })
    public void revoke(@PathVariable UUID id) {
        sessionService.revokeOwn(tenantContext.requireUserId(), id);
    }
}
