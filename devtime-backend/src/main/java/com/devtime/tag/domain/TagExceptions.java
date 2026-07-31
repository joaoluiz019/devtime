package com.devtime.tag.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Exceções de regra da feature 006 (spec §27).
 *
 * <p>BR-063: toda instância nasce de um método fábrica nomeado pela regra que a origina.
 */
public final class TagExceptions {

    private TagExceptions() {}

    /** RN-507: comprimento fora de 2–40 <b>após</b> a normalização. */
    public static BusinessRuleException invalidName(String normalizedName) {
        return new InvalidTagNameException(normalizedName);
    }

    /** RN-507: nome normalizado já existente no tenant (CX-01, CX-09). */
    public static BusinessRuleException duplicateName() {
        return new DuplicateTagException();
    }

    /** RN-313 / INV-TAG-01: limite de 10 etiquetas por alvo. */
    public static BusinessRuleException limitExceeded(UUID targetId, int requested, int maximum) {
        return new TagLimitExceededException(targetId, requested, maximum);
    }

    /** RN-507. */
    public static final class InvalidTagNameException extends BusinessRuleException {
        private InvalidTagNameException(String normalizedName) {
            super(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    // O comprimento resultante é devolvido porque a normalização encolhe a entrada
                    // e, sem ele, "40 caracteres" parece contraditório com o que foi digitado.
                    Map.of("field", "name", "normalizedLength", normalizedName.length()),
                    "O nome da etiqueta deve ter entre 2 e 40 caracteres após a normalização");
        }
    }

    /**
     * RN-507.
     *
     * <p>A mensagem não repete o nome: §28 da spec proíbe o nome da etiqueta em log de aplicação, e
     * a mensagem de exceção é registrada em log pelo tratamento global.
     */
    public static final class DuplicateTagException extends BusinessRuleException {
        private DuplicateTagException() {
            super(
                    ErrorCode.TAG_NAME_DUPLICATED,
                    Map.of("field", "name"),
                    "Já existe uma etiqueta com este nome normalizado no tenant");
        }
    }

    /** RN-313. */
    public static final class TagLimitExceededException extends BusinessRuleException {
        private TagLimitExceededException(UUID targetId, int requested, int maximum) {
            super(
                    ErrorCode.TAG_LIMIT_EXCEEDED,
                    Map.of(
                            "field", "tagIds",
                            "targetId", targetId,
                            "requested", requested,
                            "maximum", maximum),
                    "Máximo de " + maximum + " etiquetas por registro");
        }
    }
}
