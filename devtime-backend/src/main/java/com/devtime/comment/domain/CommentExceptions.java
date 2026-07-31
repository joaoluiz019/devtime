package com.devtime.comment.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;

/**
 * Exceções de regra da feature 014 (spec §27).
 *
 * <p>BR-063: toda instância nasce de um método fábrica nomeado pela regra que a origina.
 */
public final class CommentExceptions {

    private CommentExceptions() {}

    /** RN-811: corpo fora de 1–10.000 caracteres após aparar (CX-01, CX-02). */
    public static BusinessRuleException bodyInvalid(int length) {
        return new CommentBodyInvalidException(length);
    }

    /**
     * RN-812: janela de 24 horas encerrada.
     *
     * <p>tickets.md §10.2 atribui {@code DEVTIME-2706} / {@code 409} a este caso. A janela existe
     * para corrigir erro recente, não para reescrever o histórico da conversa.
     */
    public static BusinessRuleException editWindowExpired(long hoursElapsed) {
        return new CommentEditWindowExpiredException(hoursElapsed);
    }

    /** RN-815 / INV-CMT-03: comentário de sistema é registro automático de fato ocorrido. */
    public static BusinessRuleException systemImmutable() {
        return new SystemCommentImmutableException();
    }

    /**
     * INV-CMT-02: {@code parentCommentId} inexistente ou de outro ticket.
     *
     * <p>Responde {@code DEVTIME-2002} / {@code 404}: {@code DEVTIME-2706} está atribuído à janela
     * de edição por {@code docs/04-api/tickets.md} §13, que precede a spec pela hierarquia IA-11, e
     * uma referência a comentário que não é alcançável no ticket é indistinguível de inexistente
     * (ART-024). A divergência está reportada em {@code CHANGELOG.md}.
     */
    public static BusinessRuleException invalidParent() {
        return new InvalidParentCommentException();
    }

    /** RN-811. */
    public static final class CommentBodyInvalidException extends BusinessRuleException {
        private CommentBodyInvalidException(int length) {
            super(
                    ErrorCode.COMMENT_BODY_INVALID,
                    Map.of("field", "body", "length", length),
                    "O comentário deve ter entre 1 e 10.000 caracteres");
        }
    }

    /** RN-812. */
    public static final class CommentEditWindowExpiredException extends BusinessRuleException {
        private CommentEditWindowExpiredException(long hoursElapsed) {
            super(
                    ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED,
                    Map.of("hoursElapsed", hoursElapsed, "windowHours", 24),
                    "O prazo de 24 horas para editar este comentário terminou");
        }
    }

    /** RN-815. */
    public static final class SystemCommentImmutableException extends BusinessRuleException {
        private SystemCommentImmutableException() {
            super(
                    ErrorCode.COMMENT_SYSTEM_IMMUTABLE,
                    Map.of(),
                    "Comentários gerados pelo sistema não podem ser alterados");
        }
    }

    /** INV-CMT-02. */
    public static final class InvalidParentCommentException extends BusinessRuleException {
        private InvalidParentCommentException() {
            super(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    Map.of("field", "parentCommentId"),
                    "Comentário de origem inválido");
        }
    }
}
