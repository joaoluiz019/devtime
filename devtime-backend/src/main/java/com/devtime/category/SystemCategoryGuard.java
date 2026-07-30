package com.devtime.category;

import com.devtime.category.domain.Category;
import com.devtime.category.domain.CategoryExceptions;
import org.springframework.stereotype.Component;

/**
 * RN-503: categoria de sistema não pode ser excluída (spec 005 §27).
 *
 * <p>Ela pode ser <b>inativada e renomeada</b> — a proteção é apenas contra a exclusão. Um tenant
 * que não usa "Code Review" a inativa; o catálogo continua com as 9 entradas, preservando
 * INV-CAT-02 mesmo que todas as categorias criadas pelo usuário sejam removidas.
 */
@Component
public class SystemCategoryGuard {

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2602} / {@code 409}
     */
    public void assertDeletable(Category category) {
        if (category.isSystem()) {
            throw CategoryExceptions.systemProtected(category.getId()); // RN-503
        }
    }
}
