package com.devtime.contract;

import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractStatus;
import com.devtime.shared.persistence.SoftDeleteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistência de {@link Contract} (spec 004 §25). */
@Repository
public interface ContractRepository extends SoftDeleteRepository<Contract> {

    @Query("SELECT COUNT(c) > 0 FROM Contract c WHERE c.code = :code")
    boolean existsByCode(@Param("code") String code);

    /** Caminho inverso da chave do ticket (RN-302): do código legível ao identificador. */
    @Query("SELECT c FROM Contract c WHERE c.code = :code")
    Optional<Contract> findByCode(@Param("code") String code);

    /**
     * Maior código já emitido no tenant, base do próximo sequencial.
     *
     * <p>A ordenação é textual, e não numérica, porque o formato {@code CT-0000} é de largura fixa
     * com zeros à esquerda — nele a ordem lexicográfica coincide com a numérica até {@code
     * CT-9999}.
     */
    @Query("SELECT MAX(c.code) FROM Contract c WHERE c.code LIKE 'CT-%'")
    Optional<String> findHighestCode();

    @Query("SELECT c FROM Contract c WHERE c.clientId = :clientId ORDER BY c.code ASC")
    List<Contract> findByClientId(@Param("clientId") UUID clientId);

    @Query(
            """
            SELECT COUNT(c) FROM Contract c
             WHERE c.clientId = :clientId
               AND c.status IN :statuses
            """)
    long countByClientIdAndStatusIn(
            @Param("clientId") UUID clientId, @Param("statuses") List<ContractStatus> statuses);

    /**
     * Contratos em operação, para os cartões do painel (specs/010 §13.4).
     *
     * <p>Recai sobre {@code idx_contracts_active_dashboard} (V031), parcial em {@code ACTIVE} e
     * {@code SUSPENDED}: {@code DRAFT}, {@code ENDED} e {@code CANCELLED} acumulam ao longo do
     * tempo e nunca aparecem no painel.
     */
    @Query(
            """
            SELECT c FROM Contract c
             WHERE c.status IN :statuses
             ORDER BY c.code ASC
            """)
    List<Contract> findByStatusIn(@Param("statuses") java.util.Collection<ContractStatus> statuses);

    /**
     * A mesma consulta restrita aos contratos vinculados ao membro (permissions.md §9, nota ²).
     *
     * <p>Separada em vez de um {@code IN} opcional porque um {@code IN} com coleção vazia ou nula
     * não tem semântica estável em JPQL — e a diferença entre "sem restrição" e "nenhum vínculo" é
     * exatamente a que não pode ser confundida: a primeira devolveria a carteira inteira a quem não
     * pode vê-la (INV-DSH-02).
     */
    @Query(
            """
            SELECT c FROM Contract c
             WHERE c.status IN :statuses
               AND c.id IN :ids
             ORDER BY c.code ASC
            """)
    List<Contract> findByStatusInAndIdIn(
            @Param("statuses") java.util.Collection<ContractStatus> statuses,
            @Param("ids") java.util.Collection<UUID> ids);

    /**
     * RN-606: contratos {@code ACTIVE} cujo término é exatamente a data informada.
     *
     * <p>Consulta de job de plataforma: percorre todos os tenants porque o lembrete precisa
     * alcançar todas as organizações. O contexto de tenant é definido pelo chamador a cada iteração
     * (BR-049), e o {@code tenantId} viaja no resultado para tornar isso possível.
     */
    @Query(
            """
            SELECT c FROM Contract c
             WHERE c.endDate = :endDate
               AND c.status = com.devtime.contract.domain.ContractStatus.ACTIVE
            """)
    List<Contract> findActiveEndingOn(@Param("endDate") java.time.LocalDate endDate);

    /**
     * FA-05: contratos {@code ACTIVE} cuja vigência já terminou.
     *
     * <p>Varredura de plataforma. {@code <=} e não {@code =} pela mesma razão de {@code
     * findScheduledDue}: uma execução perdida não pode deixar o contrato vigente indefinidamente
     * depois do fim — o que permitiria registrar horas fora da vigência.
     */
    @Query(
            """
            SELECT c FROM Contract c
             WHERE c.status = com.devtime.contract.domain.ContractStatus.ACTIVE
               AND c.endDate IS NOT NULL
               AND c.endDate <= :reference
             ORDER BY c.endDate ASC
            """)
    List<Contract> findActiveEndedBy(
            @Param("reference") java.time.LocalDate reference,
            org.springframework.data.domain.Pageable pageable);

    /**
     * entities.md §9: contratos {@code ACTIVE} por cliente, para a reconciliação noturna de {@code
     * 003}.
     *
     * <p>Varredura de plataforma agrupada, como {@code findActiveEndedBy}. {@code SUSPENDED} não
     * entra: o contador conta contratos <b>ativos</b>, e é essa a definição que {@code
     * ClientDeletionGuard} usa para decidir se o cliente pode ser excluído.
     */
    @Query(
            """
            SELECT c.clientId, COUNT(c) FROM Contract c
             WHERE c.status = com.devtime.contract.domain.ContractStatus.ACTIVE
             GROUP BY c.clientId
            """)
    List<Object[]> countActiveByClient();
}
