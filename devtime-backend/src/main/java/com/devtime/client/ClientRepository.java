package com.devtime.client;

import com.devtime.client.domain.Client;
import com.devtime.shared.persistence.SoftDeleteRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Client} (spec 003 §25).
 *
 * <p>A busca com filtros usa {@code JpaSpecificationExecutor}, herdado de {@link
 * SoftDeleteRepository}: BR-169 exige {@code Specification} ou parâmetros vinculados em consulta
 * dinâmica, nunca concatenação de string (BR-168).
 */
@Repository
public interface ClientRepository extends SoftDeleteRepository<Client> {

    /** RN-404: unicidade de nome por tenant sem diferenciar caixa. */
    @Query(
            """
            SELECT COUNT(c) > 0 FROM Client c
             WHERE lower(c.name) = lower(:name)
               AND (:excludedId IS NULL OR c.id <> :excludedId)
            """)
    boolean existsByNameIgnoreCase(
            @Param("name") String name, @Param("excludedId") UUID excludedId);

    /** RN-403: unicidade de documento por tenant, aplicável apenas quando informado. */
    @Query(
            """
            SELECT COUNT(c) > 0 FROM Client c
             WHERE c.documentNumber = :documentNumber
               AND (:excludedId IS NULL OR c.id <> :excludedId)
            """)
    boolean existsByDocumentNumber(
            @Param("documentNumber") String documentNumber, @Param("excludedId") UUID excludedId);
}
