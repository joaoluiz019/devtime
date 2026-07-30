package com.devtime.shared.tenancy;

import jakarta.persistence.EntityManager;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Gerenciador de transações que aplica {@link TenantAwareInterceptor} a cada sessão aberta.
 *
 * <p>backend.md §7.2 exige que o filtro seja ativado "em toda abertura de sessão". Com {@code
 * spring.jpa.open-in-view=false}, a sessão é criada junto com a transação — logo o início da
 * transação é o único ponto por onde <b>toda</b> sessão passa, incluindo as abertas pelos métodos
 * transacionais dos repositórios Spring Data.
 *
 * <p>Alternativas rejeitadas: um {@code @Aspect} sobre {@code @Transactional} depende de ordenação
 * frágil em relação ao interceptor de transação e não cobre transações iniciadas fora dos pontos de
 * corte; um {@code Filter} de servlet não funciona porque, sem open-in-view, ainda não existe
 * sessão quando ele executa.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    private final transient TenantAwareInterceptor tenantAwareInterceptor;

    public TenantAwareTransactionManager(TenantAwareInterceptor tenantAwareInterceptor) {
        this.tenantAwareInterceptor = tenantAwareInterceptor;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManager entityManager = boundEntityManager();
        if (entityManager != null) {
            tenantAwareInterceptor.enableTenantFilter(entityManager);
        }
    }

    private EntityManager boundEntityManager() {
        Object resource =
                TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        return resource instanceof EntityManagerHolder holder ? holder.getEntityManager() : null;
    }
}
