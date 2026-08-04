package com.devtime.report;

import com.devtime.report.domain.ExportStatus;
import com.devtime.report.domain.ReportExecution;
import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.shared.tenancy.CrossTenant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link ReportExecution} (§25 de specs/012).
 *
 * <p>CP-19: nenhuma outra feature acessa este repositório (AR-02, BR-002). {@code 012} é folha no
 * grafo e não publica interface pública alguma (§22.2).
 *
 * <p>Salvo os métodos marcados com {@link CrossTenant}, todas as consultas são restritas ao tenant
 * da sessão pelo filtro {@code tenantFilter} (ART-022); BR-046: nenhuma escreve {@code tenant_id =
 * ?} manualmente.
 *
 * <p><b>Toda leitura de usuário é restrita ao solicitante</b> (SG-04). Não existe aqui um {@code
 * findById} de uso direto: quem chega pelo identificador passa por {@link
 * #findByIdAndRequester(UUID, UUID)}, e uma exportação de terceiro é indistinguível de inexistente
 * — ART-024 aplicado dentro do tenant, porque o arquivo é dado que sai do sistema.
 */
@Repository
public interface ReportExecutionRepository extends SoftDeleteRepository<ReportExecution> {

    /**
     * Exportações do solicitante, da mais recente para a mais antiga (§8).
     *
     * <p>Índice {@code idx_report_exec_tenant_user}. Sem ele, listar as próprias exportações
     * varreria as de todo o tenant para depois descartar — o que tornaria a enumeração de
     * exportações de terceiros um problema de custo, e não só de filtro (SG-04).
     */
    @Query(
            """
            SELECT e FROM ReportExecution e
             WHERE e.requestedBy = :requesterId
             ORDER BY e.createdAt DESC
            """)
    Page<ReportExecution> findByRequester(
            @Param("requesterId") UUID requesterId, Pageable pageable);

    /** SG-04: acompanhar, baixar e cancelar só alcançam a própria exportação. */
    @Query(
            """
            SELECT e FROM ReportExecution e
             WHERE e.id = :id
               AND e.requestedBy = :requesterId
            """)
    Optional<ReportExecution> findByIdAndRequester(
            @Param("id") UUID id, @Param("requesterId") UUID requesterId);

    /**
     * ART-074 / CE-R-12: a mesma chave devolve a mesma exportação.
     *
     * <p>Restrita ao solicitante, e não apenas ao tenant, pela mesma razão do índice único de V032:
     * duas pessoas do mesmo tenant podem escolher a mesma chave sem que uma queira o arquivo da
     * outra — e devolver o arquivo alheio por coincidência de chave seria vazamento.
     */
    @Query(
            """
            SELECT e FROM ReportExecution e
             WHERE e.requestedBy = :requesterId
               AND e.idempotencyKey = :idempotencyKey
            """)
    Optional<ReportExecution> findByIdempotencyKey(
            @Param("requesterId") UUID requesterId, @Param("idempotencyKey") String idempotencyKey);

    /**
     * Fila do {@code ExportProcessorJob} (§22.4): {@code QUEUED} e {@code FAILED} com tentativa
     * restante.
     *
     * <p>Índice parcial {@code idx_report_exec_queued}. O teto de tentativas é filtrado na consulta
     * e não no laço: uma execução esgotada que voltasse a cada 30 segundos só para ser descartada
     * gastaria o lote inteiro do job (BR-186) sem processar nada.
     */
    @CrossTenant(
            reason =
                    "Job de plataforma: a fila de exportação percorre todos os tenants e o contexto"
                            + " é definido a cada item (backend.md §7.4, BR-049)")
    @Query(
            """
            SELECT e FROM ReportExecution e
             WHERE e.status IN :statuses
               AND e.attemptCount < :maxAttempts
             ORDER BY e.createdAt ASC
            """)
    List<ReportExecution> findPendingWork(
            @Param("statuses") List<ExportStatus> statuses,
            @Param("maxAttempts") short maxAttempts,
            Pageable page);

    /**
     * Fila do {@code ExportExpiryJob} (§22.4): concluídas cujo prazo de 7 dias venceu.
     *
     * <p>Índice parcial {@code idx_report_exec_expiry}. O job precisa achar o que expirou para
     * <b>remover o binário</b> (SG-09) — um arquivo que permanece no storage depois da expiração é
     * dado pessoal fora de qualquer controle de acesso (§19.1).
     */
    @CrossTenant(
            reason =
                    "Job de plataforma: a expiração percorre todos os tenants e o contexto é"
                            + " definido a cada item (backend.md §7.4, BR-049)")
    @Query(
            """
            SELECT e FROM ReportExecution e
             WHERE e.status = com.devtime.report.domain.ExportStatus.COMPLETED
               AND e.expiresAt <= :now
             ORDER BY e.expiresAt ASC
            """)
    List<ReportExecution> findExpired(@Param("now") Instant now, Pageable page);

    /**
     * §8.1: teto de 20 exportações por hora por tenant ({@code 429}).
     *
     * <p>Conta pelo instante de criação. As canceladas ficam de fora, porque o
     * {@code @SQLRestriction} da entidade as remove — cancelar devolve a cota. É a leitura coerente
     * com o índice único de idempotência de V032, que também libera a chave ao cancelar: nos dois
     * casos, cancelar significa "não quero mais esta", e repetir o pedido depois é legítimo.
     */
    @Query(
            """
            SELECT COUNT(e) FROM ReportExecution e
             WHERE e.createdAt >= :since
            """)
    long countSince(@Param("since") Instant since);
}
