package com.devtime.user;

import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.shared.tenancy.CrossTenant;
import com.devtime.user.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link User}.
 *
 * <p>{@code User} não é tenant-scoped (ART-013), mas a busca por e-mail é marcada
 * {@code @CrossTenant} porque é um dos usos exaustivamente autorizados em backend.md §7.4 — e a
 * marcação torna o uso auditável em revisão (BR-045) em vez de invisível.
 */
@Repository
public interface UserRepository extends SoftDeleteRepository<User> {

    /**
     * AU-03: a busca é por {@code lower(email)}, casando com o índice único parcial {@code
     * uq_users_email}. Sem normalizar, {@code Rafael@x.com} e {@code rafael@x.com} seriam contas
     * distintas.
     */
    @CrossTenant(reason = "O login ocorre antes da seleção de tenant (backend.md §7.4)")
    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoringCase(String email);

    /**
     * Carga em lote dos nomes de exibição.
     *
     * <p>{@code @CrossTenant} porque {@code users} não possui {@code tenant_id} (ART-013): a
     * consulta é inerentemente global. O recorte por tenant é aplicado por quem chama, que só
     * fornece identificadores já obtidos de entidades tenant-scoped (ticket, comentário,
     * membership).
     */
    @CrossTenant(reason = "users é tabela global (ART-013); o recorte é aplicado pelo chamador")
    @Query("SELECT u FROM User u WHERE u.id IN :ids")
    List<User> findAllByIdIn(@Param("ids") Collection<java.util.UUID> ids);

    /** §6.2 de specs/014-comments: resolução de menções pelo identificador de exibição. */
    @CrossTenant(reason = "users é tabela global (ART-013); o chamador filtra por membership ativo")
    @Query("SELECT u FROM User u WHERE lower(u.displayName) IN :handles")
    List<User> findAllByDisplayNameIn(@Param("handles") Collection<String> handles);

    /**
     * {@code UnlockExpiredAccountsJob}: contas cujo bloqueio já venceu (§11 de spec 001).
     *
     * <p>Processada em lote pelo job, mas a mesma condição é aplicada no login para que uma conta
     * não permaneça bloqueada nos até dez minutos entre execuções (TS-001-08 passo 6).
     */
    @CrossTenant(reason = "users é tabela global (ART-013); o job percorre todas as contas")
    @Query("SELECT u FROM User u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil <= :reference")
    List<User> findLockExpired(@Param("reference") java.time.Instant reference);

    /**
     * Filtro {@code search} da listagem de membros (users.md §7.1).
     *
     * <p>O termo chega já em minúsculas de quem chama. A comparação é por {@code LIKE} sobre nome e
     * e-mail — os dois campos que a tela exibe e pelos quais o usuário procura.
     */
    @CrossTenant(
            reason = "users é tabela global (ART-013); o chamador cruza com os vínculos do tenant")
    @Query(
            """
            SELECT u.id FROM User u
             WHERE lower(u.fullName) LIKE CONCAT('%', :term, '%')
                OR lower(u.email) LIKE CONCAT('%', :term, '%')
            """)
    List<java.util.UUID> findIdsMatching(@Param("term") String term);
}
