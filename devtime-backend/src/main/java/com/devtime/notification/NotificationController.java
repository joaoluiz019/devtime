package com.devtime.notification;

import com.devtime.notification.dto.NotificationRequests.NotificationFilter;
import com.devtime.notification.dto.NotificationResponses.MarkAllReadResponse;
import com.devtime.notification.dto.NotificationResponses.NotificationReadResponse;
import com.devtime.notification.dto.NotificationResponses.NotificationResponse;
import com.devtime.notification.dto.NotificationResponses.UnreadCountResponse;
import com.devtime.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Central de notificações (notifications.md §7 e §8).
 *
 * <p><b>Não existe rota de criação</b> (CP-12, RS-05, CA-15). Notificações nascem exclusivamente de
 * eventos de domínio; uma rota de criação permitiria a um usuário fabricar alertas, e nenhum caso
 * de uso a exige.
 *
 * <p>Toda operação é restrita ao destinatário do token. Notificação de outra pessoa responde {@code
 * 404}, nunca {@code 403} — a existência da notificação alheia não deve ser revelada.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificações", description = "Central de notificações do usuário autenticado")
public class NotificationController {

    private final NotificationQueryService queryService;

    @GetMapping
    @Operation(
            summary = "Lista as notificações do usuário",
            description =
                    "Ordenação fixa em `createdAt,desc` — uma central ordenada por outro critério"
                            + " esconderia o alerta mais recente, que é o que se busca ao abri-la.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de notificações"),
        @ApiResponse(responseCode = "400", description = "DEVTIME-2006 — size acima de 100")
    })
    public PageResponse<NotificationResponse> list(
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return queryService.search(
                new NotificationFilter(read, type, severity, createdFrom, createdTo), pageable);
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Contagem de não lidas",
            description =
                    "Endpoint leve, consultado ao carregar toda tela. Recai sobre um índice"
                            + " **parcial** sobre `read_at IS NULL`: em um usuário com 5.000"
                            + " notificações e 3 não lidas, o índice tem 3 entradas.")
    @ApiResponse(responseCode = "200", description = "Total e quebra por severidade")
    public UnreadCountResponse unreadCount() {
        return queryService.unreadCount();
    }

    @PostMapping("/{id}/read")
    @Operation(
            summary = "Marca como lida",
            description =
                    "Idempotente: remarcar não altera `readAt` — o instante da primeira leitura é a"
                            + " informação com valor. A resposta traz a contagem atualizada.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Marcada como lida"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — de outro destinatário")
    })
    public NotificationReadResponse markRead(@PathVariable UUID id) {
        return queryService.markRead(id);
    }

    @PostMapping("/{id}/unread")
    @Operation(summary = "Marca como não lida")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Marcada como não lida"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002")
    })
    public NotificationReadResponse markUnread(@PathVariable UUID id) {
        return queryService.markUnread(id);
    }

    @PostMapping("/read-all")
    @Operation(
            summary = "Marca todas como lidas",
            description = "Atualização em lote; não carrega as notificações uma a uma.")
    @ApiResponse(responseCode = "200", description = "Quantidade marcada e contagem restante")
    public MarkAllReadResponse markAllRead() {
        return queryService.markAllRead();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Exclui a notificação da central",
            description =
                    "Exclusão lógica (RN-003). Notificações **lidas** há mais de 90 dias são"
                            + " removidas automaticamente (RN-609); as não lidas nunca são.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Excluída"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002")
    })
    public void delete(@PathVariable UUID id) {
        queryService.delete(id);
    }
}
