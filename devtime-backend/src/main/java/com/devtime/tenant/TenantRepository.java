package com.devtime.tenant;

import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.tenant.domain.Tenant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Tenant}.
 *
 * <p>Não é tenant-scoped por natureza (ART-013): {@code Tenant} é a própria raiz de isolamento,
 * logo nenhum método aqui exige {@code @CrossTenant}.
 */
@Repository
public interface TenantRepository extends SoftDeleteRepository<Tenant> {

    /** INV-TEN-01: usado para garantir unicidade global do slug antes da inserção. */
    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Carga em lote das organizações do usuário, para {@code GET /auth/tenants}.
     *
     * <p>Em lote e não uma a uma: a lista de seleção tem tamanho igual ao número de vínculos, e uma
     * consulta por item seria N+1 em uma tela que abre logo após o login (QY-03).
     */
    @org.springframework.data.jpa.repository.Query("SELECT t FROM Tenant t WHERE t.id IN :ids")
    java.util.List<Tenant> findAllByIdIn(
            @org.springframework.data.repository.query.Param("ids")
                    java.util.Collection<java.util.UUID> ids);
}
