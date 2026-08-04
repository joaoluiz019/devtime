package com.devtime.tag;

import com.devtime.tag.domain.TicketTagLink;
import com.devtime.tag.domain.TicketTagLinkId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência dos vínculos {@code ticket_tags} (spec 006 §25).
 *
 * <p>Estende {@link JpaRepository} e não {@code SoftDeleteRepository} porque a tabela de junção não
 * possui {@code deleted_at}: desvincular uma etiqueta é remover a aresta, não preservá-la excluída
 * (§9.3 da spec 006). P-03 protege entidades de domínio; uma linha de junção não é dado de negócio.
 *
 * <p>O filtro de tenant continua ativo — {@link TicketTagLink} declara {@code @Filter} —, então
 * nenhuma consulta aqui escreve {@code tenant_id = ?} manualmente (BR-046).
 */
@Repository
public interface TicketTagRepository extends JpaRepository<TicketTagLink, TicketTagLinkId> {

    @Query("SELECT l.tagId FROM TicketTagLink l WHERE l.ticketId = :ticketId")
    List<UUID> findTagIdsByTicketId(@Param("ticketId") UUID ticketId);

    /** Carga em lote das etiquetas de vários tickets, evitando N+1 na listagem e no quadro. */
    @Query("SELECT l FROM TicketTagLink l WHERE l.ticketId IN :ticketIds")
    List<TicketTagLink> findByTicketIdIn(@Param("ticketIds") Collection<UUID> ticketIds);

    /**
     * Tickets que possuem <b>todas</b> as etiquetas informadas (conjunção — tickets.md §6).
     *
     * <p>O {@code HAVING COUNT(...) = :tagCount} é o que transforma a disjunção natural do {@code
     * IN} em conjunção: só passam os tickets cujo número de vínculos casados iguala o número de
     * etiquetas pedidas.
     */
    @Query(
            """
            SELECT l.ticketId FROM TicketTagLink l
             WHERE l.tagId IN :tagIds
             GROUP BY l.ticketId
            HAVING COUNT(l.tagId) = :tagCount
            """)
    List<UUID> findTicketIdsWithAllTags(
            @Param("tagIds") Collection<UUID> tagIds, @Param("tagCount") long tagCount);

    /**
     * Remoção em lote na exclusão da etiqueta (§9.3 da spec 006).
     *
     * <p>{@code DELETE} em massa por índice, nunca carregando entidades: uma etiqueta usada em todo
     * o tenant pode ter milhares de vínculos (CX-12).
     */
    @Modifying
    @Query("DELETE FROM TicketTagLink l WHERE l.tagId = :tagId")
    int deleteByTagId(@Param("tagId") UUID tagId);

    @Modifying
    @Query("DELETE FROM TicketTagLink l WHERE l.ticketId = :ticketId AND l.tagId IN :tagIds")
    int deleteByTicketIdAndTagIdIn(
            @Param("ticketId") UUID ticketId, @Param("tagIds") Collection<UUID> tagIds);

    @Modifying
    @Query("DELETE FROM TicketTagLink l WHERE l.ticketId = :ticketId")
    int deleteByTicketId(@Param("ticketId") UUID ticketId);

    /**
     * INV-TAG-04: contagem real de vínculos por etiqueta, para a reconciliação noturna.
     *
     * <p>Uma consulta agrupada, nunca uma por etiqueta (CP-12): um tenant com centenas de etiquetas
     * transformaria o job em centenas de consultas por noite.
     */
    @Query(
            """
            SELECT new com.devtime.tag.TagLinkCount(l.tagId, COUNT(l))
              FROM TicketTagLink l
             GROUP BY l.tagId
            """)
    List<TagLinkCount> countLinksByTag();
}
