package com.devtime.shared.persistence;

import java.time.Instant;
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
