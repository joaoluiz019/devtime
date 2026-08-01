package com.devtime.timer;

import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.shared.tenancy.CrossTenant;
import com.devtime.timer.domain.Timer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Timer} (spec 009 §25).
 *
 * <p>As consultas de leitura por tenant usam o filtro automático (ART-022). A exceção é {@link
 * #findActiveByUser}, documentada abaixo.
 */
@Repository
public interface TimerRepository extends SoftDeleteRepository<Timer> {

    /**
     * RN-150: cronômetro ativo do usuário, <b>em qualquer tenant</b>.
     *
     * <p>Única consulta da feature que ignora o filtro de tenant, e ela precisa ignorá-lo: RN-150
     * limita a um cronômetro ativo por <b>pessoa</b>, não por organização (CE-13, CX-01). Com o
     * filtro ativo, alguém que participa de dois tenants poderia manter dois cronômetros rodando —
     * exatamente o que a regra proíbe, e o que o índice único parcial {@code uq_timers_active_user}
     * (sem {@code tenant_id}) impede no banco.
     *
     * <p>A consulta é nativa por necessidade técnica: o {@code @Filter} de Hibernate é aplicado a
     * consultas JPQL e não a SQL nativo, e {@code @CrossTenant} é um marcador de revisão, não um
     * desativador em tempo de execução (ver o javadoc da anotação).
     *
     * <p><b>O escopo continua estreito:</b> o parâmetro é o usuário autenticado, nunca um
     * identificador vindo da requisição (OWN-05). Nenhum cronômetro de terceiro é alcançável por
     * aqui.
     */
    @CrossTenant(
            reason =
                    "RN-150/CE-13: o limite de um cronômetro ativo é por usuário entre TODOS os"
                            + " tenants. Com filtro de tenant a regra seria inaplicável e dois"
                            + " cronômetros simultâneos passariam. O escopo é o próprio usuário"
                            + " autenticado (OWN-05), nunca terceiros.")
    @Query(
            value =
                    """
                    SELECT * FROM timers
                     WHERE user_id = :userId
                       AND status IN ('RUNNING', 'PAUSED')
                       AND deleted_at IS NULL
                     LIMIT 1
                    """,
            nativeQuery = true)
    Optional<Timer> findActiveByUser(@Param("userId") UUID userId);

    /** {@code GET /timers/active}: cronômetros ativos do tenant corrente (TIMER_VIEW_ANY). */
    @Query(
            """
            SELECT t FROM Timer t
             WHERE t.status IN (com.devtime.timer.domain.TimerStatus.RUNNING,
                                com.devtime.timer.domain.TimerStatus.PAUSED)
             ORDER BY t.startedAt ASC
            """)
    List<Timer> findActiveInTenant();

    /** RN-311: {@code PAUSED} conta como ativo — o trabalho não terminou, apenas parou. */
    @Query(
            """
            SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END FROM Timer t
             WHERE t.ticketId = :ticketId
               AND t.status IN (com.devtime.timer.domain.TimerStatus.RUNNING,
                                com.devtime.timer.domain.TimerStatus.PAUSED)
            """)
    boolean existsActiveForTicket(@Param("ticketId") UUID ticketId);

    /** RN-311: identificadores dos cronômetros ativos, devolvidos no corpo do erro. */
    @Query(
            """
            SELECT t.id FROM Timer t
             WHERE t.ticketId = :ticketId
               AND t.status IN (com.devtime.timer.domain.TimerStatus.RUNNING,
                                com.devtime.timer.domain.TimerStatus.PAUSED)
            """)
    List<UUID> findActiveIdsForTicket(@Param("ticketId") UUID ticketId);

    /**
     * RN-240: cronômetros ativos cujo trabalho pertenceria ao período.
     *
     * <p>O critério é o ticket estar em um contrato do período e o {@code startedAt} cair dentro
     * dele. Como {@code timers} não desnormaliza o contrato, o filtro chega por lista de tickets
     * resolvida pelo chamador — o que mantém a consulta dentro da própria feature (AR-02).
     */
    @Query(
            """
            SELECT t.id FROM Timer t
             WHERE t.ticketId IN :ticketIds
               AND t.status IN (com.devtime.timer.domain.TimerStatus.RUNNING,
                                com.devtime.timer.domain.TimerStatus.PAUSED)
            """)
    List<UUID> findActiveIdsForTickets(@Param("ticketIds") List<UUID> ticketIds);

    /**
     * RN-163 e RN-164: cronômetros ativos iniciados antes do limiar, em <b>todos</b> os tenants.
     *
     * <p>Job de plataforma (BR-049): o contexto de tenant é definido a cada iteração pelo chamador.
     * A consulta é JPQL e o filtro está inativo porque o job roda sem tenant selecionado — a
     * ausência de tenant nunca é tratada como "todos" em requisição HTTP, mas é exatamente o que um
     * job de plataforma precisa.
     */
    @Query(
            """
            SELECT t FROM Timer t
             WHERE t.status IN (com.devtime.timer.domain.TimerStatus.RUNNING,
                                com.devtime.timer.domain.TimerStatus.PAUSED)
               AND t.startedAt < :threshold
             ORDER BY t.startedAt ASC
            """)
    List<Timer> findActiveStartedBefore(@Param("threshold") Instant threshold);

    /** RN-165: abandonados fora da janela de 7 dias, descartados pelo job de limpeza. */
    @Query(
            """
            SELECT t FROM Timer t
             WHERE t.status = com.devtime.timer.domain.TimerStatus.ABANDONED
               AND t.startedAt < :threshold
            """)
    List<Timer> findAbandonedStartedBefore(@Param("threshold") Instant threshold);

    /** {@code GET /timers/abandoned}: recuperáveis do usuário no tenant corrente. */
    @Query(
            """
            SELECT t FROM Timer t
             WHERE t.userId = :userId
               AND t.status = com.devtime.timer.domain.TimerStatus.ABANDONED
             ORDER BY t.startedAt DESC
            """)
    List<Timer> findAbandonedByUser(@Param("userId") UUID userId);

    /** RN-460: cronômetros do membro removido, descartados na mesma transação. */
    @Query(
            """
            SELECT t FROM Timer t
             WHERE t.userId = :userId
               AND t.status IN (com.devtime.timer.domain.TimerStatus.RUNNING,
                                com.devtime.timer.domain.TimerStatus.PAUSED)
            """)
    List<Timer> findActiveByUserInTenant(@Param("userId") UUID userId);
}
