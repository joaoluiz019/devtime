package com.devtime.tag;

import com.devtime.tag.domain.WorkLogTagLink;
import com.devtime.tag.domain.WorkLogTagLinkId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência dos vínculos {@code work_log_tags} (V028).
 *
 * <p>Simétrica a {@link TicketTagRepository}: estende {@link JpaRepository} porque a tabela de
 * junção não possui {@code deleted_at}, e o filtro de tenant continua ativo pela anotação
 * {@code @Filter} da entidade (BR-046).
 */
@Repository
public interface WorkLogTagRepository extends JpaRepository<WorkLogTagLink, WorkLogTagLinkId> {

    @Query("SELECT l.tagId FROM WorkLogTagLink l WHERE l.workLogId = :workLogId")
    List<UUID> findTagIdsByWorkLogId(@Param("workLogId") UUID workLogId);

    /** Carga em lote das etiquetas de vários registros, evitando N+1 na listagem (§20). */
    @Query("SELECT l FROM WorkLogTagLink l WHERE l.workLogId IN :workLogIds")
    List<WorkLogTagLink> findByWorkLogIdIn(@Param("workLogIds") Collection<UUID> workLogIds);

    /** Registros que possuem <b>todas</b> as etiquetas informadas — conjunção, como em tickets. */
    @Query(
            """
            SELECT l.workLogId FROM WorkLogTagLink l
             WHERE l.tagId IN :tagIds
             GROUP BY l.workLogId
            HAVING COUNT(l.tagId) = :tagCount
            """)
    List<UUID> findWorkLogIdsWithAllTags(
            @Param("tagIds") Collection<UUID> tagIds, @Param("tagCount") long tagCount);

    @Modifying
    @Query("DELETE FROM WorkLogTagLink l WHERE l.tagId = :tagId")
    int deleteByTagId(@Param("tagId") UUID tagId);

    @Modifying
    @Query("DELETE FROM WorkLogTagLink l WHERE l.workLogId = :workLogId AND l.tagId IN :tagIds")
    int deleteByWorkLogIdAndTagIdIn(
            @Param("workLogId") UUID workLogId, @Param("tagIds") Collection<UUID> tagIds);

    @Modifying
    @Query("DELETE FROM WorkLogTagLink l WHERE l.workLogId = :workLogId")
    int deleteByWorkLogId(@Param("workLogId") UUID workLogId);
}
