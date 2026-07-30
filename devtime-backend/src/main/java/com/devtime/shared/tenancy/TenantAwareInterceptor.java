package com.devtime.shared.tenancy;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Ativa o filtro Hibernate de tenant na sessão corrente (backend.md §7.2).
 *
 * <p>Camada 2 da defesa em profundidade de {@code security.md} §6.1: com o filtro ativo, todo
 * {@code SELECT} de entidade tenant-scoped recebe {@code WHERE tenant_id = ?} automaticamente. É
 * por isso que BR-046 proíbe escrever {@code tenant_id = ?} manualmente em query — o filtro já o
 * faz, e duplicar a condição esconde os casos em que ela foi esquecida.
 *
 * <p>Foi escolhido {@code @Filter} em vez de {@code @TenantId} porque {@code @Filter} pode ser
 * desativado pontualmente na sessão, o que é pré-requisito para os usos de {@link CrossTenant}
 * previstos em backend.md §7.4 (login e renovação de token ocorrem antes de haver tenant). Com
 * {@code @TenantId}, o discriminador é imposto por Hibernate sem escape possível, e o fluxo de
 * login ficaria inviável.
 */
@Component
@RequiredArgsConstructor
public class TenantAwareInterceptor {

    private final TenantContext tenantContext;

    /**
     * Habilita o filtro quando há tenant selecionado.
     *
     * <p>Quando não há — token de pré-seleção ou rota pública — o filtro permanece desativado, e
     * nenhuma entidade tenant-scoped pode ser consultada sem passar por {@link CrossTenant}. A
     * ausência de tenant nunca é tratada como "todos os tenants".
     */
    public void enableTenantFilter(EntityManager entityManager) {
        UUID tenantId = tenantContext.currentTenantId().orElse(null);
        if (tenantId == null) {
            return;
        }
        entityManager
                .unwrap(Session.class)
                .enableFilter(TenantScopedEntity.TENANT_FILTER)
                .setParameter(TenantScopedEntity.TENANT_PARAM, tenantId);
    }
}
