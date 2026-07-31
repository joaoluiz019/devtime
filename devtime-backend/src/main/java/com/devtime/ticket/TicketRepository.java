package com.devtime.ticket;

import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.ticket.domain.Ticket;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Ticket} (spec 007 §25).
 *
 * <p>Todas as consultas são restritas ao tenant da sessão pelo filtro {@code tenantFilter}
 * (ART-022); BR-046: nenhuma escreve {@code tenant_id = ?} manualmente.
 */
@Repository
public interface TicketRepository extends SoftDeleteRepository<Ticket> {

    /**
     * Maior número já emitido no contrato, base do próximo sequencial (RN-302).
     *
     * <p>A serialização entre criações simultâneas vem do lock consultivo de {@link
     * TicketNumberGenerator}, não desta consulta: {@code MAX(...)} não trava linha alguma e, sem o
     * lock, duas transações leriam o mesmo valor (CP-03, CX-01).
     */
    @Query("SELECT COALESCE(MAX(t.number), 0) FROM Ticket t WHERE t.contractId = :contractId")
    int findHighestNumber(@Param("contractId") UUID contractId);

    /** Resolução de {@code GET /tickets/by-key/{key}} após a decomposição da chave. */
    @Query("SELECT t FROM Ticket t WHERE t.contractId = :contractId AND t.number = :number")
    Optional<Ticket> findByContractIdAndNumber(
            @Param("contractId") UUID contractId, @Param("number") int number);

    @Query("SELECT t FROM Ticket t WHERE t.id IN :ids")
    List<Ticket> findAllByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Quadro (kanban) em <b>uma</b> consulta agrupada.
     *
     * <p>CP-14: uma consulta por coluna custaria sete vezes mais. O limite por coluna é aplicado na
     * montagem da resposta, sobre este resultado ordenado por prioridade e atualização.
     */
    @Query(
            """
            SELECT t FROM Ticket t
             WHERE (:contractId IS NULL OR t.contractId = :contractId)
               AND (:assigneeId IS NULL OR t.assigneeId = :assigneeId)
             ORDER BY t.status ASC,
                      CASE t.priority
                           WHEN com.devtime.ticket.domain.TicketPriority.URGENT THEN 0
                           WHEN com.devtime.ticket.domain.TicketPriority.HIGH THEN 1
                           WHEN com.devtime.ticket.domain.TicketPriority.MEDIUM THEN 2
                           ELSE 3
                      END ASC,
                      t.updatedAt DESC
            """)
    List<Ticket> findBoardGrouped(
            @Param("contractId") UUID contractId, @Param("assigneeId") UUID assigneeId);

    /**
     * Contratos em que o usuário é relator ou responsável de algum ticket (permissions.md §9).
     *
     * <p>Alimenta o escopo de dados de {@code MEMBER} sobre clientes e contratos. É {@code
     * DISTINCT} porque um membro costuma ter vários tickets no mesmo contrato.
     */
    @Query(
            """
            SELECT DISTINCT t.contractId FROM Ticket t
             WHERE t.reporterId = :userId
                OR t.assigneeId = :userId
            """)
    List<UUID> findContractIdsByParticipant(@Param("userId") UUID userId);

    /**
     * RN-308: incremento atômico dos totais desnormalizados.
     *
     * <p>CP-12 proíbe reagregar todos os work logs do ticket: a operação dispara em toda escrita de
     * work log, o caminho mais quente do sistema, e reagregar seria linear no número de registros.
     * Ler-modificar-escrever também está fora — perderia atualizações sob concorrência.
     */
    @Modifying
    @Query(
            """
            UPDATE Ticket t
               SET t.spentMinutes = t.spentMinutes + :spentDelta,
                   t.billableMinutes = t.billableMinutes + :billableDelta
             WHERE t.id = :id
            """)
    int adjustTotals(
            @Param("id") UUID id,
            @Param("spentDelta") int spentDelta,
            @Param("billableDelta") int billableDelta);
}
