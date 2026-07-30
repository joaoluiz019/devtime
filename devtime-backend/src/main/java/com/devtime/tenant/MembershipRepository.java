package com.devtime.tenant;

import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.shared.tenancy.CrossTenant;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
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
}
