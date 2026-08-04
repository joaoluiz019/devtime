package com.devtime.category;

import com.devtime.category.domain.CategoryHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Leitura do catálogo histórico (spec 005 §22, OB-04).
 *
 * <p>Restrita ao tenant da sessão pelo filtro {@code tenantFilter}, como qualquer outra consulta
 * JPQL (ART-022). O que ela <b>não</b> aplica é o corte de exclusão lógica — ver {@link
 * CategoryHistory}.
 */
@Repository
public interface CategoryHistoryRepository extends JpaRepository<CategoryHistory, UUID> {

    /** Mesma ordenação da listagem viva, para que os dois resultados sejam comparáveis. */
    @Query("SELECT c FROM CategoryHistory c ORDER BY c.sortOrder ASC, c.name ASC")
    List<CategoryHistory> findAllIncludingDeleted();
}
