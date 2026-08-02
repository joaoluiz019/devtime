package com.devtime.worklog;

import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.worklog.domain.WorkLog;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link WorkLog} (spec 008 §25).
 *
 * <p>Todas as consultas são restritas ao tenant da sessão pelo filtro {@code tenantFilter}
 * (ART-022); BR-046: nenhuma escreve {@code tenant_id = ?} manualmente.
 */
@Repository
public interface WorkLogRepository extends SoftDeleteRepository<WorkLog> {

    /**
     * RN-102: existe sessão do mesmo usuário sobrepondo o intervalo?
     *
     * <p>A condição reproduz literalmente {@link
     * com.devtime.worklog.domain.WorkLogInterval#overlaps} — comparação <b>estrita nos dois
     * lados</b>, intervalos semi-abertos. Os dois pontos precisam concordar: o record é o oráculo
     * testado contra a tabela normativa de §6.2, e esta consulta é o que efetivamente roda.
     *
     * <p>CP-05 proíbe comparar em memória: carregar os registros do usuário para filtrar em Java
     * seria correto e inaceitavelmente lento no caminho mais quente do sistema. O {@code EXISTS}
     * recai sobre {@code idx_work_logs_overlap} e retorna ao primeiro conflito.
     *
     * <p>Retorna apenas o <b>identificador</b>, com {@code LIMIT 1}: no caminho feliz — nenhuma
     * sobreposição, a esmagadora maioria das chamadas — nada é materializado. Os dados do registro
     * conflitante, exigidos pela mensagem de erro, são carregados em uma segunda consulta que só
     * executa quando já se sabe que há conflito.
     *
     * @param excludeId próprio identificador na edição; {@code null} na criação (CX-17)
     */
    @Query(
            """
            SELECT w.id FROM WorkLog w
             WHERE w.userId = :userId
               AND (:excludeId IS NULL OR w.id <> :excludeId)
               AND w.startedAt < :endedAt
               AND :startedAt < w.endedAt
             ORDER BY w.startedAt ASC
             LIMIT 1
            """)
    Optional<UUID> findFirstOverlappingId(
            @Param("userId") UUID userId,
            @Param("startedAt") Instant startedAt,
            @Param("endedAt") Instant endedAt,
            @Param("excludeId") UUID excludeId);

    /** RN-219: soma canônica do consumo do período, usada na reconciliação do fechamento. */
    @Query(
            """
            SELECT COALESCE(SUM(CASE WHEN w.billable = TRUE THEN w.netMinutes ELSE 0 END), 0)
              FROM WorkLog w
             WHERE w.contractPeriodId = :periodId
            """)
    int sumBillableMinutesByPeriod(@Param("periodId") UUID periodId);

    /** RN-223: minutos não faturáveis do período — visíveis no relatório, fora do saldo. */
    @Query(
            """
            SELECT COALESCE(SUM(CASE WHEN w.billable = FALSE THEN w.netMinutes ELSE 0 END), 0)
              FROM WorkLog w
             WHERE w.contractPeriodId = :periodId
            """)
    int sumNonBillableMinutesByPeriod(@Param("periodId") UUID periodId);

    /** RN-305 / RN-307: existência real de horas no ticket, publicada a {@code 007}. */
    @Query("SELECT COUNT(w) FROM WorkLog w WHERE w.ticketId = :ticketId")
    long countByTicketId(@Param("ticketId") UUID ticketId);

    /** RN-458: registros preservados de um membro removido, publicada a {@code 002}. */
    @Query("SELECT COUNT(w) FROM WorkLog w WHERE w.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    /** RN-505: quantidade de registros vinculados a uma categoria, publicada a {@code 005}. */
    @Query("SELECT COUNT(w) FROM WorkLog w WHERE w.categoryId = :categoryId")
    long countByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("SELECT w FROM WorkLog w WHERE w.contractPeriodId = :periodId ORDER BY w.startedAt ASC")
    List<WorkLog> findByPeriod(@Param("periodId") UUID periodId);

    /**
     * Contratos em que o usuário registrou horas (permissions.md §9).
     *
     * <p>Metade da definição operacional do escopo de dados de {@code MEMBER}; a outra metade vem
     * dos tickets em que ele é relator ou responsável.
     */
    @Query("SELECT DISTINCT w.contractId FROM WorkLog w WHERE w.userId = :userId")
    List<UUID> findContractIdsByUser(@Param("userId") UUID userId);

    @Query("SELECT w FROM WorkLog w WHERE w.id IN :ids")
    List<WorkLog> findAllByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * RN-241 passo 3: trava todos os registros do período em <b>uma</b> instrução.
     *
     * <p>CX-17: um período pode ter 10.000 registros. Carregar as entidades para chamar um setter
     * em cada uma tornaria o fechamento — já a operação mais pesada do sistema — proporcional ao
     * volume, com a transação segurando o lock por todo esse tempo.
     *
     * @return quantidade de registros travados, informada no resumo do fechamento
     */
    @Modifying
    @Query(
            """
            UPDATE WorkLog w
               SET w.lockedAt = :lockedAt,
                   w.updatedAt = :lockedAt
             WHERE w.contractPeriodId = :periodId
               AND w.lockedAt IS NULL
            """)
    int lockByPeriod(@Param("periodId") UUID periodId, @Param("lockedAt") Instant lockedAt);

    /** RN-243: a reabertura limpa {@code lockedAt}, também em lote. */
    @Modifying
    @Query(
            """
            UPDATE WorkLog w
               SET w.lockedAt = NULL,
                   w.updatedAt = :updatedAt
             WHERE w.contractPeriodId = :periodId
               AND w.lockedAt IS NOT NULL
            """)
    int unlockByPeriod(@Param("periodId") UUID periodId, @Param("updatedAt") Instant updatedAt);

    /**
     * Agregação diária do calendário (P22, §7 de worklogs.md).
     *
     * <p>O agrupamento é por {@code workDate}, que já é a data local do tenant (RN-108): agrupar
     * pelo instante em UTC colocaria uma sessão das 22h no dia seguinte para tenants a leste de
     * Greenwich — exatamente o erro que RN-009 existe para impedir.
     */
    @Query(
            """
            SELECT w.workDate AS day,
                   SUM(w.netMinutes) AS totalMinutes,
                   SUM(CASE WHEN w.billable = TRUE THEN w.netMinutes ELSE 0 END) AS billableMinutes,
                   COUNT(w) AS entryCount
              FROM WorkLog w
             WHERE w.workDate BETWEEN :from AND :to
               AND (:userId IS NULL OR w.userId = :userId)
             GROUP BY w.workDate
             ORDER BY w.workDate ASC
            """)
    List<DailyAggregate> aggregateByDay(
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("userId") UUID userId);

    /** Projeção do calendário — nunca a entidade (BR-107). */
    interface DailyAggregate {
        LocalDate getDay();

        long getTotalMinutes();

        long getBillableMinutes();

        long getEntryCount();
    }

    /** Totais por categoria de {@code GET /work-logs/totals} (§8 de worklogs.md). */
    @Query(
            """
            SELECT w.categoryId AS categoryId,
                   SUM(w.netMinutes) AS totalMinutes,
                   COUNT(w) AS entryCount
              FROM WorkLog w
             WHERE w.id IN :ids
             GROUP BY w.categoryId
            """)
    List<CategoryAggregate> aggregateByCategory(@Param("ids") Collection<UUID> ids);

    /** Projeção dos totais por categoria. */
    interface CategoryAggregate {
        UUID getCategoryId();

        long getTotalMinutes();

        long getEntryCount();
    }

    /**
     * INV-WKL-08: registros do período cuja {@code workDate} caiu fora do intervalo dele.
     *
     * <p>Consumida por {@code WorkLogConsistencyJob}, que <b>alerta e não corrige</b> (CP-17): uma
     * divergência aqui significa que a resolução de período falhou, e escondê-la com uma correção
     * automática impediria a investigação do defeito.
     *
     * <p>As datas do período chegam por parâmetro em vez de a consulta cruzar {@code
     * ContractPeriod}: AR-02 proíbe que esta feature dependa da entidade de {@code 004}. O job
     * obtém o intervalo pela interface pública {@code ContractPeriodService} e o repassa.
     */
    @Query(
            """
            SELECT w FROM WorkLog w
             WHERE w.contractPeriodId = :periodId
               AND (w.workDate < :startDate OR w.workDate > :endDate)
            """)
    List<WorkLog> findWithWorkDateOutside(
            @Param("periodId") UUID periodId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * RN-102 / INV-WKL-05: pares de registros sobrepostos já persistidos, para o job de
     * consistência.
     *
     * <p>Uma linha aqui é alerta {@code ERROR} crítico: significa que a validação da aplicação
     * falhou (R-01). Como não existe constraint {@code EXCLUDE} para RN-102 (OB-02), esta consulta
     * é a terceira camada de defesa — a que descobre o que as duas primeiras deixaram passar.
     */
    @Query(
            """
            SELECT a FROM WorkLog a, WorkLog b
             WHERE a.userId = b.userId
               AND a.id <> b.id
               AND a.startedAt < b.endedAt
               AND b.startedAt < a.endedAt
               AND a.startedAt <= b.startedAt
             ORDER BY a.startedAt ASC
            """)
    List<WorkLog> findOverlappingPairs();
}
