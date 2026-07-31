package com.devtime.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTOs de entrada da feature 014 (tickets.md §10.1, spec §23).
 *
 * <p>Campos deliberadamente ausentes: {@code authorId} (sempre o autenticado), {@code isSystem}
 * (apenas RN-815 o define) e {@code mentionedUserIds} (derivado do corpo). Campo ausente do
 * contrato é barreira mais forte que campo validado.
 */
public final class CommentRequests {

    public static final int BODY_MIN = 1;
    public static final int BODY_MAX = 10_000;

    private CommentRequests() {}

    /**
     * @param parentCommentId responder a uma resposta vincula à raiz (RN-814); ausente cria raiz
     */
    @Schema(name = "CommentCreateRequest")
    public record CommentCreateRequest(
            @NotBlank @Size(min = BODY_MIN, max = BODY_MAX) String body, UUID parentCommentId) {}

    /** {@code parentCommentId} está ausente: a hierarquia é imutável (INV-CMT-05). */
    @Schema(name = "CommentUpdateRequest")
    public record CommentUpdateRequest(
            @NotBlank @Size(min = BODY_MIN, max = BODY_MAX) String body, @NotNull Long version) {}
}
