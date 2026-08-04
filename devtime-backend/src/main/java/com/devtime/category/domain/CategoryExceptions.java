package com.devtime.category.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;

/**
 * Exceções de regra da feature 005 (spec §27).
 *
 * <p>BR-063: cada instância vem de um método fábrica nomeado pela regra que a origina — nunca de um
 * construtor genérico. Reunir as fábricas em um ponto único mantém código, mensagem e detalhes
 * coerentes entre si e torna imediato verificar que toda regra da §12 tem representação em código.
 */
public final class CategoryExceptions {

    private CategoryExceptions() {}

    /** RN-502: já existe categoria com este nome no tenant, sem diferenciar caixa. */
    public static BusinessRuleException duplicateName(String name) {
        return new DuplicateCategoryException(name);
    }

    /** RN-503: categoria de sistema é inativável e renomeável, nunca excluível. */
    public static BusinessRuleException systemProtected(UUID categoryId) {
        return new SystemCategoryException(categoryId);
    }

    /** RN-505 passo 4: há registros vinculados e nenhuma substituta foi informada. */
    public static BusinessRuleException replacementRequired(UUID categoryId, long workLogsCount) {
        return new ReplacementRequiredException(categoryId, workLogsCount);
    }

    /** users.md §8.3: substituta inexistente, inativa ou igual à excluída. */
    public static BusinessRuleException invalidReplacement(String reason) {
        return new InvalidReplacementCategoryException(reason);
    }

    /** users.md §8.4: a lista de reordenação deve conter todas as categorias do tenant. */
    public static BusinessRuleException invalidReorder(String reason) {
        return new InvalidCategoryReorderException(reason);
    }

    /** RN-502. */
    public static final class DuplicateCategoryException extends BusinessRuleException {
        private DuplicateCategoryException(String name) {
            super(
                    ErrorCode.CATEGORY_NAME_DUPLICATED,
                    Map.of("field", "name"),
                    "Categoria já existente: " + name);
        }
    }

    /** RN-503. */
    public static final class SystemCategoryException extends BusinessRuleException {
        private SystemCategoryException(UUID categoryId) {
            super(
                    ErrorCode.CATEGORY_SYSTEM_PROTECTED,
                    Map.of("categoryId", categoryId),
                    "Categoria de sistema não pode ser excluída");
        }
    }

    /**
     * users.md §8.4 / CX-10 a CX-12.
     *
     * <p>Usa {@code DEVTIME-2000} com {@code 422}: a lista está sintaticamente correta, mas não
     * satisfaz a condição de completude — é falha de validação, não de formato.
     */
    public static final class InvalidCategoryReorderException extends BusinessRuleException {
        private InvalidCategoryReorderException(String reason) {
            super(
                    ErrorCode.VALIDATION_FAILED,
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("field", "orderedIds", "reason", reason),
                    "Reordenação inválida: " + reason);
        }
    }

    /**
     * RN-505.
     *
     * <p>Os detalhes trazem a contagem: "existem registros" não diz ao usuário se ele está prestes
     * a mover 3 ou 30.000 horas de classificação.
     */
    public static final class ReplacementRequiredException extends BusinessRuleException {
        private ReplacementRequiredException(UUID categoryId, long workLogsCount) {
            super(
                    ErrorCode.CATEGORY_REPLACEMENT_REQUIRED,
                    Map.of(
                            "field",
                            "replacementCategoryId",
                            "categoryId",
                            categoryId,
                            "workLogsCount",
                            workLogsCount),
                    "Categoria possui registros de horas e exige substituta");
        }
    }

    /** users.md §8.3. */
    public static final class InvalidReplacementCategoryException extends BusinessRuleException {
        private InvalidReplacementCategoryException(String reason) {
            super(
                    ErrorCode.CATEGORY_REPLACEMENT_INVALID,
                    Map.of("field", "replacementCategoryId", "reason", reason),
                    "Categoria substituta inválida: " + reason);
        }
    }
}
