package com.devtime.tag;

import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.tag.domain.Tag;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Tag} (spec 006 §25).
 *
 * <p>Todas as consultas são restritas ao tenant da sessão pelo filtro {@code tenantFilter}
 * (ART-022); BR-046: nenhuma escreve {@code tenant_id = ?} manualmente.
 */
@Repository
public interface TagRepository extends SoftDeleteRepository<Tag> {

    /**
     * Ordenação padrão de users.md §9.1: mais usadas primeiro, desempate estável por nome.
     *
     * <p>O padrão chega pronto (`%termo%`) e o {@code cast(... as String)} é obrigatório, não
     * estilo: sem o cast, Hibernate 6 envia o parâmetro nulo sem tipo e o PostgreSQL recusa a
     * comparação com {@code bytea}. Montar o padrão fora daqui evita repetir o parâmetro dentro de
     * um {@code CONCAT}, onde a segunda ocorrência voltaria a chegar sem tipo.
     */
    @Query(
            """
            SELECT t FROM Tag t
             WHERE (cast(:namePattern as String) IS NULL OR t.name LIKE cast(:namePattern as String))
               AND (:minUsage IS NULL OR t.usageCount >= :minUsage)
             ORDER BY t.usageCount DESC, t.name ASC
            """)
    List<Tag> search(@Param("namePattern") String namePattern, @Param("minUsage") Integer minUsage);

    /** RN-507: a unicidade é sempre verificada sobre o nome já normalizado. */
    @Query(
            """
            SELECT COUNT(t) > 0 FROM Tag t
             WHERE t.name = :name
               AND (:excludedId IS NULL OR t.id <> :excludedId)
            """)
    boolean existsByNormalizedName(
            @Param("name") String name, @Param("excludedId") UUID excludedId);

    @Query("SELECT t FROM Tag t WHERE t.name = :name")
    Optional<Tag> findByNormalizedName(@Param("name") String name);

    @Query("SELECT t FROM Tag t WHERE t.id IN :ids")
    List<Tag> findAllByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Autocompletar: limitado no servidor, não apenas no cliente.
     *
     * <p>O limite de 20 (§20 da spec) é do servidor porque a consulta dispara a cada tecla
     * digitada; um limite apenas no cliente ainda trafegaria a lista inteira.
     */
    @Query(
            """
            SELECT t FROM Tag t
             WHERE t.name LIKE CONCAT('%', :term, '%')
             ORDER BY t.usageCount DESC, t.name ASC
            """)
    List<Tag> searchForAutocomplete(@Param("term") String term, Pageable limit);

    /**
     * RN-508: órfãs há mais de 90 dias — o limiar é estritamente maior (CX-14).
     *
     * <p>Sustentada por {@code idx_tags_tenant_orphan}, índice parcial sobre {@code usage_count =
     * 0}.
     */
    @Query(
            """
            SELECT t FROM Tag t
             WHERE t.usageCount = 0
               AND t.updatedAt < :threshold
             ORDER BY t.updatedAt ASC, t.name ASC
            """)
    List<Tag> findOrphansOlderThan(@Param("threshold") Instant threshold);

    /**
     * INV-TAG-04: incremento atômico, nunca leitura-modificação-escrita.
     *
     * <p>Sob dois vínculos simultâneos à mesma etiqueta, ler o contador e gravar o valor calculado
     * perderia uma das atualizações. O {@code UPDATE ... SET x = x + ?} delega a soma ao banco,
     * onde a linha está travada pela própria escrita.
     */
    @Modifying
    @Query("UPDATE Tag t SET t.usageCount = t.usageCount + :delta WHERE t.id = :id")
    int adjustUsageCount(@Param("id") UUID id, @Param("delta") int delta);
}
