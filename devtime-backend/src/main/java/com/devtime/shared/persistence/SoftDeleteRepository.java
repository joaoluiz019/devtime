package com.devtime.shared.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/**
 * Repositório base das entidades com exclusão lógica (ART-051, BR-030).
 *
 * <p>Substitui {@code delete}/{@code deleteById} de {@link JpaRepository}, que executariam {@code
 * DELETE} físico — proibido em entidade de domínio (P-03). Os métodos herdados de escrita
 * destrutiva continuam existindo na interface do Spring Data, por isso a proibição também é
 * verificada por ArchUnit e revisão.
 *
 * <p>A exclusão é feita por {@code UPDATE} explícito, e não carregando a entidade e chamando um
 * setter, para que a operação seja atômica e não dependa de {@code @Version} — a exclusão não
 * conflita logicamente com uma edição concorrente do mesmo registro.
 *
 * @param <T> entidade de domínio
 */
@NoRepositoryBean
public interface SoftDeleteRepository<T extends BaseEntity>
        extends JpaRepository<T, UUID>, JpaSpecificationExecutor<T> {

    /**
     * Busca por identificador <b>respeitando o filtro de tenant</b>.
     *
     * <p>A sobrescrita é obrigatória, não conveniência. O {@code findById} padrão de Spring Data
     * usa {@code EntityManager.find()}, e o {@code @Filter} de Hibernate <b>não é aplicado a {@code
     * find()}</b> — apenas a consultas. Sem esta declaração, {@code findById} devolveria registros
     * de outro tenant, violando ART-022 ("filtro automático e não-opcional") e ART-024 (recurso de
     * outro tenant deve ser indistinguível de inexistente).
     *
     * <p>Declarar a consulta em JPQL faz o filtro voltar a valer. O teste de isolamento {@code
     * readByIdMustNotFindOtherTenantRecord} é o que impede esta regressão.
     */
    @Override
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id")
    Optional<T> findById(@Param("id") UUID id);

    @Modifying
    @Query(
            """
            UPDATE #{#entityName} e
               SET e.deletedAt = :deletedAt,
                   e.deletedBy = :deletedBy,
                   e.updatedAt = :deletedAt,
                   e.updatedBy = :deletedBy
             WHERE e.id = :id
               AND e.deletedAt IS NULL
            """)
    int softDelete(
            @Param("id") UUID id,
            @Param("deletedAt") Instant deletedAt,
            @Param("deletedBy") UUID deletedBy);
}
