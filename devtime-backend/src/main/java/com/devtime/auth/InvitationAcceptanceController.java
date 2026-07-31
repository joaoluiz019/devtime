package com.devtime.auth;

import com.devtime.auth.AuthService.RequestMetadata;
import com.devtime.auth.AuthService.SessionOutcome;
import com.devtime.auth.dto.AuthRequests.AcceptInvitationRequest;
import com.devtime.auth.dto.AuthResponses.InvitationResponse;
import com.devtime.auth.dto.AuthResponses.MessageResponse;
import com.devtime.shared.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta e aceite de convite ({@code authentication.md} §5.12).
 *
 * <p>Público por necessidade: a tela de aceite precisa exibir organização e papel antes de existir
 * qualquer sessão. O que protege o endpoint é a imprevisibilidade do token — 256 bits, buscado por
 * hash —, não a autenticação.
 */
@RestController
@RequestMapping("/api/v1/auth/invitations")
@RequiredArgsConstructor
@Tag(name = "Convites", description = "Consumo de convite para uma organização (RN-457)")
public class InvitationAcceptanceController {

    private final InvitationAcceptanceService invitationAcceptanceService;
    private final RefreshCookieFactory cookieFactory;
    private final TenantContext tenantContext;

    @GetMapping("/{token}")
    @Operation(
            summary = "Consulta um convite",
            description =
                    "Exibe organização, papel e quem convidou. userExists indica se a conta já"
                            + " pode autenticar, para que a tela escolha entre login e cadastro.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Convite válido"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2458 — inválido ou revogado"),
        @ApiResponse(responseCode = "410", description = "DEVTIME-2457 — expirado (7 dias)")
    })
    public InvitationResponse peek(@PathVariable String token) {
        return invitationAcceptanceService.peek(token);
    }

    @PostMapping("/{token}/accept")
    @Operation(
            summary = "Aceita um convite",
            description =
                    "Ativa o vínculo. Sem sessão prévia, autentica e devolve a sessão da"
                            + " organização convidada. Com sessão ativa, CX-09 preserva a"
                            + " organização corrente e devolve apenas confirmação.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Convite aceito"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2459 — já é membro"),
        @ApiResponse(responseCode = "410", description = "DEVTIME-2457 — convite expirado")
    })
    public ResponseEntity<?> accept(
            @PathVariable String token,
            @Valid @RequestBody(required = false) AcceptInvitationRequest request,
            HttpServletRequest httpRequest) {
        UUID authenticatedUserId = tenantContext.currentUserId().orElse(null);
        SessionOutcome outcome =
                invitationAcceptanceService.accept(
                        token,
                        request,
                        authenticatedUserId,
                        new RequestMetadata(
                                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                                httpRequest.getRemoteAddr()));

        if (outcome == null) {
            // CX-09: o convidado já estava autenticado em outra organização. Nenhum cookie é
            // trocado — emitir um novo derrubaria a sessão que ele está usando agora.
            return ResponseEntity.ok(new MessageResponse("Convite aceito."));
        }
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieFactory.create(outcome.rawRefreshToken()).toString())
                .body(outcome.response());
    }
}
