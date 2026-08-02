package com.devtime.attachment;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.domain.ScanStatus;
import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.shared.tenancy.CrossTenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Attachment} (spec 015 §25).
 *
 * <p>CP-21: nenhuma outra feature acessa este repositório (AR-02, BR-002). O acesso externo é pela
 * interface {@code AttachmentService}.
 *
 * <p>Salvo o método marcado com {@link CrossTenant}, todas as consultas são restritas ao tenant da
 * sessão pelo filtro {@code tenantFilter} (ART-022); BR-046: nenhuma escreve {@code tenant_id = ?}
 * manualmente.
 */
@Repository
public interface AttachmentRepository extends SoftDeleteRepository<Attachment> {

    /**
     * Anexos do ticket, do mais recente para o mais antigo. Índice {@code idx_attachments_ticket}.
     */
    @Query(
            """
            SELECT a FROM Attachment a
             WHERE a.ticketId = :ticketId
             ORDER BY a.createdAt DESC
            """)
    List<Attachment> findByTicket(@Param("ticketId") UUID ticketId);

    /** Anexos do comentário. Índice {@code idx_attachments_comment}. */
    @Query(
            """
            SELECT a FROM Attachment a
             WHERE a.commentId = :commentId
             ORDER BY a.createdAt DESC
            """)
    List<Attachment> findByComment(@Param("commentId") UUID commentId);

    /** RN-806, alvo ticket: limite de 20. */
    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.ticketId = :ticketId")
    long countByTicket(@Param("ticketId") UUID ticketId);

    /** RN-806, alvo comentário: limite de 5. */
    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.commentId = :commentId")
    long countByComment(@Param("commentId") UUID commentId);

    /**
     * RN-805, passo 2 de §6.4: conteúdo idêntico <b>dentro do tenant</b>.
     *
     * <p>O filtro de tenant é o que restringe a busca (CP-06). Sem ele, o tempo de upload revelaria
     * que outro tenant possui o mesmo arquivo — canal de inferência que o ganho de storage não
     * compensa.
     *
     * <p>Exige {@code binaryPresent}: CX-14 — um arquivo idêntico a um {@code INFECTED} não tem
     * binário para reusar, e um novo upload precisa gravar e ser verificado de novo.
     */
    @Query(
            """
            SELECT a FROM Attachment a
             WHERE a.checksumSha256 = :checksum
               AND a.binaryPresent = true
             ORDER BY a.createdAt ASC
            """)
    List<Attachment> findByChecksum(@Param("checksum") String checksum, Pageable page);

    /**
     * RN-805, passo 5 de §6.4: quantos registros não excluídos ainda referenciam a chave.
     *
     * <p><b>Deliberadamente não é {@code @CrossTenant}.</b> A chave carrega o {@code tenantId} como
     * prefixo (integrations.md §6.2) e a deduplicação é restrita ao tenant (CP-06): duas
     * organizações nunca compartilham uma {@code storageKey}. Contar com o filtro ativo é, aqui, a
     * opção segura — uma contagem sem filtro produziria o mesmo número e abriria uma exceção de
     * isolamento que nenhuma regra pede.
     */
    @Query(
            """
            SELECT COUNT(a) FROM Attachment a
             WHERE a.storageKey = :storageKey
               AND a.binaryPresent = true
               AND a.id <> :excludedId
            """)
    long countOtherReferencesToStorageKey(
            @Param("storageKey") String storageKey, @Param("excludedId") UUID excludedId);

    /**
     * RN-801: consumo do tenant. Índice coberto {@code idx_attachments_quota}.
     *
     * <p>CX-18: soma apenas registros não excluídos com binário presente. {@code COALESCE} porque
     * um tenant sem nenhum anexo produziria {@code null}, e ER-06 proíbe devolver nulo como
     * resultado de negócio.
     */
    @Query(
            """
            SELECT COALESCE(SUM(a.sizeBytes), 0) FROM Attachment a
             WHERE a.binaryPresent = true
            """)
    long sumSizeByTenant();

    /**
     * Fila de verificação do {@code ScanWorkerJob}. Índice {@code idx_attachments_scan_queue}.
     *
     * <p>{@link CrossTenant} justificado: o worker é de plataforma e processa todos os tenants,
     * definindo o contexto a cada item (BR-049). Sem isso, a fila só avançaria quando houvesse uma
     * requisição de usuário estabelecendo um tenant — e a verificação é assíncrona justamente para
     * não depender disso.
     */
    @CrossTenant(
            reason =
                    "Job de plataforma: a fila de verificação percorre todos os tenants e o"
                            + " contexto é definido a cada item (backend.md §7.4, BR-049)")
    @Query(
            """
            SELECT a FROM Attachment a
             WHERE a.scanStatus = :status
               AND a.binaryPresent = true
             ORDER BY a.createdAt ASC
            """)
    List<Attachment> findByScanStatus(@Param("status") ScanStatus status, Pageable page);

    /**
     * Chaves de storage vivas, para o {@code OrphanBinaryJob}.
     *
     * <p>{@link CrossTenant} pelo mesmo motivo da fila: o job compara o bucket inteiro com o
     * conjunto de registros que o referenciam.
     */
    @CrossTenant(
            reason =
                    "Job de plataforma: o bucket não é particionado por tenant e a detecção de"
                            + " órfãos compara o conjunto inteiro (backend.md §7.4)")
    @Query(
            """
            SELECT DISTINCT a.storageKey FROM Attachment a
             WHERE a.binaryPresent = true
            """)
    List<String> findReferencedStorageKeys();

    /** Anexo por identificador, sem a restrição de exclusão lógica — usado pela trilha. */
    @Query("SELECT a FROM Attachment a WHERE a.id = :id")
    Optional<Attachment> findActiveById(@Param("id") UUID id);
}
