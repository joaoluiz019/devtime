package com.devtime.category;

import com.devtime.category.domain.Category;
import com.devtime.category.domain.CategoryExceptions;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Valida a categoria substituta indicada na exclusão (users.md §8.3, spec 005 §27).
 *
 * <p>A validação precede a migração (passo 5 antes do 6 na §6.1) porque migrar registros para uma
 * categoria inválida corromperia os dados de forma difícil de reverter — a categoria de origem já
 * não existiria para desfazer a operação.
 */
@Component
@RequiredArgsConstructor
public class CategoryReplacementValidator {

    private final CategoryRepository repository;

    /**
     * @param replacementId candidata a substituta
     * @param deletedId categoria sendo excluída
     * @return a substituta validada
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2605} / {@code 422}
     */
    public Category require(UUID replacementId, UUID deletedId) {
        if (replacementId == null) {
            throw CategoryExceptions.invalidReplacement("substituta não informada");
        }
        if (replacementId.equals(deletedId)) {
            // CX-07: substituir por si mesma deixaria os registros apontando para uma categoria
            // excluída, violando INV-CAT-04.
            throw CategoryExceptions.invalidReplacement("substituta igual à categoria excluída");
        }
        return repository
                .findActiveById(replacementId)
                // CX-08: migrar para uma categoria inativa criaria registros com categoria que não
                // é oferecida em novos lançamentos.
                .orElseThrow(
                        () ->
                                CategoryExceptions.invalidReplacement(
                                        "substituta inexistente ou inativa"));
    }
}
