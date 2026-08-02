package com.devtime.auth;

import com.devtime.auth.domain.RefreshToken;
import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.shared.tenancy.CrossTenant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link RefreshToken} (spec 001 §25).
 *
 * <p>{@code RefreshToken} não é tenant-scoped (database.md §7.12): a renovação pode preceder a
 * seleção de tenant. A marcação {@code @CrossTenant} na busca por hash é exigida pela §25 do spec e
 * existe para tornar o uso auditável em revisão (BR-045), não para desligar um filtro.
 */
@Repository
public interface RefreshTokenRepository extends SoftDeleteRepository<RefreshToken> {

    /**
     * RT-01/RT-02: a renovação encontra o token pelo SHA-256 do valor apresentado.
     *
     * <p>Retorna tokens revogados e rotacionados também — é justamente o que permite distinguir
     * "revogado por logout" (CX-06 → {@code DEVTIME-1004}) de "rotacionado e reapresentado" (RT-04
     * / RN-005 → {@code DEVTIME-1005}). Filtrar aqui apagaria a diferença.
     */
    @CrossTenant(reason = "A renovação pode preceder a seleção de tenant (backend.md §7.4)")
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    /** Sessões vivas do usuário, para {@code GET /auth/sessions} e para a revogação em cadeia. */
    @CrossTenant(reason = "refresh_tokens é tabela global; o recorte é o próprio usuário")
    @Query(
            """
            SELECT t FROM RefreshToken t
             WHERE t.userId = :userId
               AND t.revokedAt IS NULL
               AND t.replacedById IS NULL
               AND t.expiresAt > :reference
             ORDER BY t.createdAt DESC
            """)
    List<RefreshToken> findLiveByUserId(
            @Param("userId") UUID userId, @Param("reference") Instant reference);

    /**
     * RT-04 / RN-005: revoga toda a cadeia do usuário na detecção de reuso, e serve também a {@code
     * logout-all} e à redefinição de senha.
     *
     * <p>{@code UPDATE} em lote, e não carga em memória: o número de sessões é desconhecido e o
     * efeito precisa ser atômico dentro da transação que detectou o reuso.
     *
     * @return quantidade de tokens efetivamente revogados
     */
    @Modifying
    @Query(
            """
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt, t.updatedAt = :revokedAt
             WHERE t.userId = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    /** RN-454 / RT-06: a troca de senha preserva exclusivamente a sessão que a solicitou. */
    @Modifying
    @Query(
            """
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt, t.updatedAt = :revokedAt
             WHERE t.userId = :userId
               AND t.revokedAt IS NULL
               AND t.id <> :keptId
            """)
    int revokeAllByUserIdExcept(
            @Param("userId") UUID userId,
            @Param("keptId") UUID keptId,
            @Param("revokedAt") Instant revokedAt);

    /** RT-07: suspensão ou remoção de membership derruba as sessões daquele tenant. */
    @Modifying
    @Query(
            """
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt, t.updatedAt = :revokedAt
             WHERE t.userId = :userId
               AND t.tenantId = :tenantId
               AND t.revokedAt IS NULL
            """)
    int revokeAllByUserIdAndTenantId(
            @Param("userId") UUID userId,
            @Param("tenantId") UUID tenantId,
            @Param("revokedAt") Instant revokedAt);

    /** RN-008: o cancelamento da organização derruba todas as sessões dela, de todos os membros. */
    @Modifying
    @Query(
            """
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt, t.updatedAt = :revokedAt
             WHERE t.tenantId = :tenantId
               AND t.revokedAt IS NULL
            """)
    int revokeAllByTenantId(
            @Param("tenantId") UUID tenantId, @Param("revokedAt") Instant revokedAt);

    /**
     * RT-08: exclusão lógica dos tokens encerrados há mais de 30 dias.
     *
     * <p>Exclusão lógica e não física porque BR-030 proíbe {@code DELETE} em entidade de domínio. A
     * carência de 30 dias exigida por RT-08 é aplicada por quem chama, ao calcular {@code
     * threshold}.
     */
    @Modifying
    @Query(
            """
            UPDATE RefreshToken t
               SET t.deletedAt = :now, t.updatedAt = :now
             WHERE t.deletedAt IS NULL
               AND (t.expiresAt < :threshold
                    OR (t.revokedAt IS NOT NULL AND t.revokedAt < :threshold))
            """)
    int softDeleteSettledBefore(@Param("threshold") Instant threshold, @Param("now") Instant now);
}
