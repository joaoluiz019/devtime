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
}
