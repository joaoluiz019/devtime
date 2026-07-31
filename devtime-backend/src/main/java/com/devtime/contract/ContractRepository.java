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
}
