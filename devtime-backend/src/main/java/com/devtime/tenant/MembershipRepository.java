package com.devtime.tenant;

import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.shared.tenancy.CrossTenant;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Membership}, entidade tenant-scoped.
 *
 * <p>Os métodos sem {@code @CrossTenant} são automaticamente restritos ao tenant da sessão pelo
 * filtro {@code tenantFilter} (ART-022). BR-046: nenhum deles escreve {@code tenant_id = ?}
 * manualmente — a condição é acrescentada por Hibernate, e duplicá-la esconderia os casos em que o
 * filtro não está ativo.
 */
@Repository
public interface MembershipRepository extends SoftDeleteRepository<Membership> {

    /**
     * Tenant-scoped: retorna vazio para um membership de outro tenant, resultando em 404 (ART-024).
     */
    Optional<Membership> findByUserId(UUID userId);

    List<Membership> findByStatus(MembershipStatus status);

    /**
     * Lista os tenants disponíveis para o usuário.
     *
     * <p>Precisa cruzar tenants por definição: é o que alimenta a tela de seleção de organização,
     * que ocorre <b>antes</b> de existir um tenant selecionado.
     */
    @CrossTenant(reason = "Listar tenants disponíveis para o usuário no login (backend.md §7.4)")
    @Query(
            """
            SELECT m FROM Membership m
             WHERE m.userId = :userId
               AND m.status = com.devtime.tenant.domain.MembershipStatus.ACTIVE
            """)
    List<Membership> findActiveByUserId(UUID userId);

    /**
     * Vínculo de um usuário em uma organização específica.
     *
     * <p>O {@code tenantId} é parâmetro de negócio — a organização que o usuário está selecionando
     * ou na qual sua sessão declara estar —, não substituto do filtro automático. É por isso que a
     * consulta precisa ser {@code @CrossTenant}: em {@code POST /auth/select-tenant} ainda não
     * existe tenant na sessão, e é justamente essa chamada que decidirá qual passará a existir
     * (RN-459).
     */
    @CrossTenant(reason = "A seleção de tenant ocorre antes de existir tenant na sessão (RN-459)")
    @Query("SELECT m FROM Membership m WHERE m.tenantId = :tenantId AND m.userId = :userId")
    Optional<Membership> findByTenantIdAndUserId(
            @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * §4.2 de state-machines.md: a verificação do e-mail ativa os convites pendentes do usuário.
     *
     * <p>Cruza tenants por definição — o usuário pode ter sido convidado por várias organizações
     * antes de confirmar o e-mail, e nenhuma delas está selecionada neste momento.
     */
    @CrossTenant(reason = "A verificação de e-mail ativa convites de qualquer tenant (§4.2 SM)")
    @Query(
            """
            SELECT m FROM Membership m
             WHERE m.userId = :userId
               AND m.status = com.devtime.tenant.domain.MembershipStatus.INVITED
            """)
    List<Membership> findInvitedByUserId(@Param("userId") UUID userId);

    /**
     * RN-607: membros ativos do tenant com um dos papéis informados.
     *
     * <p>O filtro por papel entra na consulta: carregar todos os membros para descartar a maioria
     * em memória custaria proporcional ao tamanho da organização a cada avaliação de limiar, e a
     * avaliação roda a cada alteração de consumo (RN-602).
     */
    @Query(
            """
            SELECT m.userId FROM Membership m
             WHERE m.status = com.devtime.tenant.domain.MembershipStatus.ACTIVE
               AND m.role IN :roles
            """)
    List<UUID> findActiveUserIdsByRoleIn(
            @Param("roles") java.util.Collection<com.devtime.shared.security.Role> roles);

    /**
     * RN-455 / CX-02: OWNERs ativos do tenant, com <b>lock pessimista</b>.
     *
     * <p>O lock é o que torna a regra correta sob concorrência (BR-124). Duas transações rebaixando
     * OWNERs diferentes ao mesmo tempo leriam a mesma contagem e ambas passariam; com o lock, a
     * segunda espera a primeira e enxerga o estado já reduzido.
     *
     * <p>Usa {@code idx_memberships_tenant_role}, que já é parcial por {@code status = 'ACTIVE'}.
     */
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT m FROM Membership m
             WHERE m.role = com.devtime.shared.security.Role.OWNER
               AND m.status = com.devtime.tenant.domain.MembershipStatus.ACTIVE
            """)
    List<Membership> lockActiveOwners();

    /**
     * Listagem de membros (users.md §7.1).
     *
     * <p>Os filtros nulos são neutralizados na própria consulta em vez de exigirem uma {@code
     * Specification}: são apenas dois, ambos de igualdade sobre enum.
     *
     * @param userIds recorte pelo termo de busca, já resolvido em {@code users}; nulo desativa o
     *     filtro
     */
    @Query(
            """
            SELECT m FROM Membership m
             WHERE (:status IS NULL OR m.status = :status)
               AND (:role IS NULL OR m.role = :role)
               AND (:userIds IS NULL OR m.userId IN :userIds)
            """)
    org.springframework.data.domain.Page<Membership> search(
            @Param("status") MembershipStatus status,
            @Param("role") com.devtime.shared.security.Role role,
            @Param("userIds") java.util.Collection<UUID> userIds,
            org.springframework.data.domain.Pageable pageable);

    /** RN-457: convites cujo prazo de 7 dias venceu, para o {@code ExpiredInvitationJob}. */
    @CrossTenant(reason = "O job varre convites vencidos de todas as organizações (BR-049)")
    @Query(
            """
            SELECT m FROM Membership m
             WHERE m.status = com.devtime.tenant.domain.MembershipStatus.INVITED
               AND m.invitedAt IS NOT NULL
               AND m.invitedAt <= :threshold
            """)
    List<Membership> findExpiredInvitations(
            @Param("threshold") java.time.Instant threshold,
            org.springframework.data.domain.Pageable pageable);

    /**
     * RN-008 / §19.1: usuários cujo único vínculo era o tenant purgado.
     *
     * <p>{@code @CrossTenant} por necessidade: a pergunta é justamente "existe vínculo em
     * <b>outra</b> organização?", e com o filtro ativo ela seria inexprimível. O escopo permanece
     * estreito — a consulta parte dos vínculos do tenant sendo purgado, nunca de um identificador
     * de requisição.
     *
     * <p>Vínculos excluídos logicamente não contam como "outra organização": o
     * {@code @SQLRestriction} da entidade já os remove, e um membership removido não é razão para
     * preservar dado pessoal.
     */
    @CrossTenant(
            reason =
                    "RN-008: a purga só pode anonimizar quem não participa de nenhuma outra"
                            + " organização, e essa verificação atravessa tenants por definição. O"
                            + " conjunto de partida são os vínculos do tenant já purgado.")
    @Query(
            """
            SELECT m.userId FROM Membership m
             WHERE m.tenantId = :purgedTenantId
               AND NOT EXISTS (
                     SELECT 1 FROM Membership other
                      WHERE other.userId = m.userId
                        AND other.tenantId <> :purgedTenantId)
            """)
    List<UUID> findUserIdsOnlyIn(@Param("purgedTenantId") UUID purgedTenantId);
}
