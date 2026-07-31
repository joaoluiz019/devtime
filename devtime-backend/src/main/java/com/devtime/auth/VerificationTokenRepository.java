package com.devtime.auth;

import com.devtime.auth.domain.VerificationToken;
import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.shared.tenancy.CrossTenant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link VerificationToken} (spec 001 §25).
 *
 * <p>A busca é sempre por {@code (tokenHash, type)}. O tipo entra na condição para que um token
 * legítimo de um fluxo não sirva de chave para outro — ver {@link VerificationTokenType}.
 */
@Repository
public interface VerificationTokenRepository extends SoftDeleteRepository<VerificationToken> {

    /**
     * Localiza o token apresentado, inclusive consumido ou expirado.
     *
     * <p>Devolver o registro nesses estados é necessário para distinguir "inválido" ({@code
     * DEVTIME-1010} / {@code DEVTIME-2458}, {@code 404}) de "expirado ou já usado" ({@code
     * DEVTIME-1009} / {@code DEVTIME-1007} / {@code DEVTIME-2457}, {@code 410}) — distinção que
     * {@code authentication.md} §5.6 e §5.12 exigem na resposta.
     */
    @CrossTenant(reason = "Convite e verificação são consumidos antes de existir tenant na sessão")
    @Query("SELECT t FROM VerificationToken t WHERE t.tokenHash = :hash AND t.type = :type")
    Optional<VerificationToken> findByTokenHashAndType(
            @Param("hash") String hash, @Param("type") VerificationTokenType type);

    /**
     * RN-457: o reenvio invalida o token anterior do mesmo usuário e tipo.
     *
     * <p>Marca {@code invalidatedAt}, e não {@code consumedAt}, porque os dois estados exigem
     * respostas diferentes. Um link efetivamente usado responde sucesso na segunda vez (§5.6,
     * CA-08); um link substituído por reenvio precisa responder "expirado" — se respondesse
     * sucesso, o usuário que clicasse no e-mail antigo concluiria que ele valeu, e o token novo
     * ficaria órfão.
     *
     * <p>Marcar, e não excluir, preserva a evidência de que um token foi emitido e substituído —
     * informação útil quando o titular relata ter recebido dois e-mails.
     */
    @Modifying
    @Query(
            """
            UPDATE VerificationToken t
               SET t.invalidatedAt = :now, t.updatedAt = :now
             WHERE t.userId = :userId
               AND t.type = :type
               AND t.consumedAt IS NULL
               AND t.invalidatedAt IS NULL
            """)
    int invalidatePrevious(
            @Param("userId") UUID userId,
            @Param("type") VerificationTokenType type,
            @Param("now") Instant now);

    /** {@code VerificationTokenCleanupJob}: descarta consumidos e expirados (BR-030: lógico). */
    @Modifying
    @Query(
            """
            UPDATE VerificationToken t
               SET t.deletedAt = :now, t.updatedAt = :now
             WHERE t.deletedAt IS NULL
               AND (t.consumedAt IS NOT NULL
                    OR t.invalidatedAt IS NOT NULL
                    OR t.expiresAt < :threshold)
            """)
    int softDeleteSettledBefore(@Param("threshold") Instant threshold, @Param("now") Instant now);
}
