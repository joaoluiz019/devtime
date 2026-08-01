package com.devtime.notification;

import com.devtime.shared.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fluxo em tempo real por Server-Sent Events (notifications.md §7.2).
 *
 * <p>Controller próprio porque o contrato é distinto: {@code text/event-stream} com conexão longa,
 * e não uma resposta JSON. Misturá-lo ao controller da central obrigaria os dois a compartilhar
 * tratamento de erro e tempo limite que não têm em comum.
 *
 * <p><b>ST-05 / INV-NOT-04: o fluxo nunca é o único canal.</b> Se ele estiver indisponível, o
 * cliente consulta {@code unread-count} periodicamente (ST-04) e a central continua completa —
 * nenhuma notificação existe apenas aqui.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificações — fluxo", description = "Entrega em tempo real por SSE")
public class NotificationStreamController {

    private final NotificationStreamRegistry streamRegistry;
    private final TenantContext tenantContext;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasPermission(null, 'NOTIFICATION_VIEW')")
    @Operation(
            summary = "Abre o fluxo de notificações do usuário",
            description =
                    "SG-03: o fluxo é por **destinatário**, nunca por organização — um fluxo por"
                            + " tenant entregaria a cada conectado as notificações dos colegas."
                            + " Emite `notification`, `unread-count` e `heartbeat` a cada 30"
                            + " segundos (ST-01). A conexão expira com o access token e o cliente"
                            + " reconecta após renová-lo (ST-02); ao reconectar, ele **recarrega** o"
                            + " histórico, sem assumir que nada foi perdido (CP-10).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fluxo aberto"),
        @ApiResponse(
                responseCode = "429",
                description = "DEVTIME-4003 — mais de 3 conexões simultâneas (ST-03)")
    })
    public SseEmitter stream() {
        // OWN: o fluxo é resolvido pelo token, nunca por identificador de caminho — não existe rota
        // capaz de abrir o fluxo de outra pessoa (SG-03).
        return streamRegistry.open(tenantContext.requireUserId());
    }
}
