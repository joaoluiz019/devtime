package com.devtime.category;

import com.devtime.category.dto.CategoryRequests.CategoryCreateRequest;
import com.devtime.category.dto.CategoryRequests.CategoryReorderRequest;
import com.devtime.category.dto.CategoryRequests.CategoryUpdateRequest;
import com.devtime.category.dto.CategoryResponses.CategoryDeletionResponse;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import java.util.List;
import java.util.UUID;

/**
 * Interface pública da feature 005 (spec §22.2).
 *
 * <p>BR-003: é o único caminho de acesso a categorias a partir de outra feature. {@code
 * 004-contracts} a consome para validar {@code defaultCategoryId}; {@code 008}, {@code 009} e
 * {@code 012} consumirão {@link #requireActive(UUID)} e {@link #listActive()}.
 */
public interface CategoryService {

    /** Listagem ordenada por {@code sortOrder} (users.md §8.1). */
    List<CategoryResponse> list(Boolean active, String search);

    CategoryResponse getById(UUID id);

    CategoryResponse create(CategoryCreateRequest request);

    CategoryResponse update(UUID id, CategoryUpdateRequest request);

    /** RN-503/RN-505: exclusão lógica com migração para a substituta quando houver vínculos. */
    CategoryDeletionResponse delete(UUID id, UUID replacementCategoryId);

    /** users.md §8.4: reordenação atômica; a lista deve conter todas as categorias do tenant. */
    List<CategoryResponse> reorder(CategoryReorderRequest request);

    /**
     * Cria as 9 categorias de sistema do tenant (RN-501).
     *
     * <p>Interface pública para {@code 002-users}, que deve invocá-la <b>dentro</b> da transação de
     * criação do tenant: um seed fora da transação produziria tenants sem categoria, violando
     * INV-CAT-02 de forma silenciosa e irreversível.
     *
     * @return quantidade de categorias criadas; zero quando o tenant já possui catálogo (CX-14)
     */
    int seedDefaults();

    /**
     * Categoria ativa do tenant, para uso por outras features.
     *
     * <p>Retorna DTO e não a entidade: AR-02 proíbe que {@code 004} dependa de {@code
     * category.domain}, e a entidade traria consigo o mapeamento JPA para dentro da outra feature.
     *
     * @throws com.devtime.shared.error.EntityNotFoundException {@code DEVTIME-2002} quando
     *     inexistente, de outro tenant ou inativa
     */
    CategoryResponse requireActive(UUID id);

    /** Categorias ativas ordenadas, para seletores e relatórios. */
    List<CategoryResponse> listActive();
}
