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
}
