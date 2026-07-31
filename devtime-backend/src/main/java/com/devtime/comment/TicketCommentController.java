package com.devtime.comment;

import com.devtime.comment.dto.CommentRequests.CommentCreateRequest;
import com.devtime.comment.dto.CommentResponses.CommentResponse;
import com.devtime.comment.dto.CommentResponses.CommentThreadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversa do ticket (tickets.md §10.1).
 *
 * <p>BR-080: nenhuma regra de negócio aqui. A permissão é verificada na camada de serviço (BR-161).
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comentários", description = "Conversa e registros automáticos do ticket")
public class TicketCommentController {

    private final CommentService commentService;

    @GetMapping
    @Operation(
            summary = "Lista a conversa do ticket",
            description =
                    "Raízes com suas respostas, paginadas por cursor. As respostas de uma raiz são"
                            + " carregadas em lote, sem N+1.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversa do ticket"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — ticket de outro tenant")
    })
    public CommentThreadResponse list(
            @PathVariable UUID ticketId,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(defaultValue = "20") int size) {
        return commentService.listByTicket(ticketId, cursor, size);
    }

    @PostMapping
    @Operation(
            summary = "Comenta no ticket",
            description =
                    "RN-814: responder a uma resposta vincula ao comentário raiz. RN-813: menções"
                            + " `@` são resolvidas contra membros ativos; as não resolvidas"
                            + " permanecem como texto, sem erro.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Comentário criado"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — ticket ou comentário de origem inválido"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2705 — corpo fora de 1–10.000")
    })
    public ResponseEntity<CommentResponse> create(
            @PathVariable UUID ticketId, @Valid @RequestBody CommentCreateRequest request) {
        CommentResponse created = commentService.create(ticketId, request);
        // BR-088: 201 sempre acompanha Location. A rota de manutenção é /comments/{id}.
        return ResponseEntity.created(URI.create("/api/v1/comments/" + created.id())).body(created);
    }
}
