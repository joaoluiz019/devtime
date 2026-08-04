package com.devtime.tag;

import com.devtime.tag.domain.TagHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Leitura do catálogo histórico de etiquetas (spec 006 §22).
 *
 * <p>Restrita ao tenant da sessão pelo filtro {@code tenantFilter} (ART-022); o que ela não aplica
 * é o corte de exclusão lógica — ver {@link TagHistory}.
 */
@Repository
public interface TagHistoryRepository extends JpaRepository<TagHistory, UUID> {

    @Query("SELECT t FROM TagHistory t ORDER BY t.name ASC")
    List<TagHistory> findAllIncludingDeleted();
}
