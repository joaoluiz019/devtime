package com.devtime.comment;

import com.devtime.comment.dto.CommentRequests.CommentUpdateRequest;
import com.devtime.comment.dto.CommentResponses.CommentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manutenção de um comentário (tickets.md §10.2).
 *
 * <p>Rota separada de {@code /tickets/{id}/comments} porque a operação é sobre o comentário, não
 * sobre o ticket — e o cliente que edita já possui o identificador do comentário.
 */
@RestController
@RequestMapping("/api/v1/comments/{id}")
@RequiredArgsConstructor
@Tag(name = "Comentários — manutenção", description = "Edição e moderação de comentários")
public class CommentController {

    private final CommentService commentService;

    @PatchMapping
    @Operation(
            summary = "Edita o próprio comentário",
            description =
                    "RN-812: apenas o autor, em até 24 horas. `ADMIN` e `OWNER` moderam por"
                            + " exclusão, mas nunca editam — `COMMENT_UPDATE_ANY` não existe no"
                            + " catálogo de permissões, porque editar o que outra pessoa disse é"
                            + " falsificação.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comentário editado"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1103 — não é o autor"),
        @ApiResponse(
                responseCode = "409",
                description =
                        "DEVTIME-2706 (janela de 24h encerrada), DEVTIME-2707 (comentário de"
                                + " sistema) ou DEVTIME-2004 (versão)"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2705 — corpo fora de 1–10.000")
    })
    public CommentResponse update(
            @PathVariable UUID id, @Valid @RequestBody CommentUpdateRequest request) {
        return commentService.update(id, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Exclui um comentário",
            description =
                    "O autor exclui dentro da janela de 24 horas; quem possui COMMENT_DELETE_ANY"
                            + " modera a qualquer momento. Excluir uma raiz preserva as respostas.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Excluído logicamente"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1103"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2706 ou DEVTIME-2707")
    })
    public void delete(@PathVariable UUID id) {
        commentService.delete(id);
    }
}
