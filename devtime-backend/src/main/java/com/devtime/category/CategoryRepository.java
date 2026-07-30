package com.devtime.category;

import com.devtime.category.domain.Category;
import com.devtime.shared.persistence.SoftDeleteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Category} (spec 005 §25).
 *
 * <p>Todos os métodos são restritos ao tenant da sessão pelo filtro {@code tenantFilter} (ART-022).
 * BR-046: nenhuma consulta escreve {@code tenant_id = ?} manualmente.
 */
@Repository
public interface CategoryRepository extends SoftDeleteRepository<Category> {

    /** Listagem padrão de users.md §8.1: {@code sortOrder,asc}, com desempate estável por nome. */
    @Query("SELECT c FROM Category c ORDER BY c.sortOrder ASC, c.name ASC")
    List<Category> findAllOrdered();

    @Query(
            """
            SELECT c FROM Category c
             WHERE c.active = true
             ORDER BY c.sortOrder ASC, c.name ASC
            """)
    List<Category> findActiveOrdered();

    /**
     * RN-502: unicidade sem diferenciar caixa.
     *
     * <p>{@code excludedId} permite reutilizar a verificação na atualização, em que o próprio
     * registro não deve ser considerado conflito.
     */
    @Query(
            """
            SELECT COUNT(c) > 0 FROM Category c
             WHERE lower(c.name) = lower(:name)
               AND (:excludedId IS NULL OR c.id <> :excludedId)
            """)
    boolean existsByNameIgnoreCase(
            @Param("name") String name, @Param("excludedId") UUID excludedId);

    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.active = true")
    Optional<Category> findActiveById(@Param("id") UUID id);

    @Query("SELECT COUNT(c) FROM Category c")
    long countAll();
}
