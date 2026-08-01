package com.devtime.notification;

import com.devtime.notification.dto.NotificationRequests.NotificationPreferencesRequest;
import com.devtime.notification.dto.NotificationResponses.NotificationPreferencesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Preferências de notificação do usuário autenticado (notifications.md §9).
 *
 * <p><b>NT-01 / RN-608: a preferência silencia o e-mail, nunca o histórico.</b> Um tipo silenciado
 * continua gerando notificação in-app — o silenciamento afeta o canal externo, e a central
 * permanece completa.
 */
@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
@Tag(name = "Notificações — preferências", description = "Silenciamento por tipo e chave de e-mail")
public class NotificationPreferencesController {

    private final NotificationQueryService queryService;

    @GetMapping
    @Operation(
            summary = "Consulta as preferências de notificação",
            description =
                    "`availableTypes` traz o catálogo com `canMute`, para que a interface liste os"
                            + " tipos sem replicá-los — adicionar um tipo não deve exigir alterar o"
                            + " frontend.")
    @ApiResponse(responseCode = "200", description = "Preferências e catálogo de tipos")
    public NotificationPreferencesResponse preferences() {
        return queryService.preferences();
    }

    @PatchMapping
    @Operation(
            summary = "Altera as preferências de notificação",
            description =
                    "Atualização parcial: campo ausente mantém o valor atual. Tipos com `canMute ="
                            + " false` — contrato excedido e anexo infectado — não podem ser"
                            + " silenciados: um tem impacto financeiro direto e o outro é incidente de"
                            + " segurança.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Preferências atualizadas"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-4001 — tipo não silenciável; DEVTIME-2000 — tipo inválido")
    })
    public NotificationPreferencesResponse update(
            @Valid @RequestBody NotificationPreferencesRequest request) {
        return queryService.updatePreferences(request);
    }
}
