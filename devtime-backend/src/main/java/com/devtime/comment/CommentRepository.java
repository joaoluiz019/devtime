package com.devtime.comment;

import com.devtime.comment.domain.Comment;
import com.devtime.shared.persistence.SoftDeleteRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Comment} (spec 014 §25).
 *
 * <p>Todas as consultas são restritas ao tenant da sessão pelo filtro {@code tenantFilter}
 * (ART-022); BR-046: nenhuma escreve {@code tenant_id = ?} manualmente.
 */
@Repository
public interface CommentRepository extends SoftDeleteRepository<Comment> {

    /**
     * Raízes do ticket, paginadas por <b>cursor</b>.
     *
     * <p>Cursor e não {@code OFFSET}: um ticket com 500 comentários teria a última página
     * progressivamente mais lenta com deslocamento (§20.1 da spec).
     */
    @Query(
            """
            SELECT c FROM Comment c
             WHERE c.ticketId = :ticketId
               AND c.parentCommentId IS NULL
               AND (cast(:cursor as Instant) IS NULL OR c.createdAt < :cursor)
             ORDER BY c.createdAt DESC
            """)
    List<Comment> findRootsByTicket(
            @Param("ticketId") UUID ticketId, @Param("cursor") Instant cursor, Pageable page);

    /**
     * Respostas de várias raízes em <b>uma</b> consulta.
     *
     * <p>Uma consulta por raiz produziria N+1 numa listagem de 20 raízes (§25 da spec).
     */
    @Query(
            """
            SELECT c FROM Comment c
             WHERE c.parentCommentId IN :parentIds
             ORDER BY c.createdAt ASC
            """)
    List<Comment> findRepliesByParents(@Param("parentIds") Collection<UUID> parentIds);

    /** Todos os comentários do ticket, para a linha do tempo (§9.1 de tickets.md). */
    @Query("SELECT c FROM Comment c WHERE c.ticketId = :ticketId ORDER BY c.createdAt DESC")
    List<Comment> findByTicket(@Param("ticketId") UUID ticketId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.ticketId = :ticketId")
    long countByTicket(@Param("ticketId") UUID ticketId);

    /** INV-ATT-01: {@code 015-attachments} valida o alvo do anexo por aqui. */
    @Query("SELECT COUNT(c) > 0 FROM Comment c WHERE c.id = :commentId")
    boolean existsById(@Param("commentId") UUID commentId);
}
