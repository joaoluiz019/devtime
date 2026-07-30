package com.devtime.shared.config;

import com.devtime.shared.tenancy.TenantAwareInterceptor;
import com.devtime.shared.tenancy.TenantAwareTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;

/** Configuração de persistência (backend.md §5). */
@Configuration
@EnableJpaRepositories(basePackages = "com.devtime")
@EntityScan(basePackages = "com.devtime")
public class JpaConfig {

    /**
     * Relógio único da aplicação.
     *
     * <p>BR-140/BR-141: nenhum código chama {@code Instant.now()} diretamente. Ter o {@link Clock}
     * como bean é o que torna possível substituí-lo por um relógio fixo em teste (BR-205), sem o
     * qual qualquer teste de cálculo temporal seria dependente do momento da execução.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Substitui o {@code JpaTransactionManager} padrão para ativar o filtro de tenant.
     *
     * <p>ART-022 exige que o filtro seja "automático e não-opcional". Trocar o gerenciador de
     * transações — em vez de acrescentar um aspecto opcional — é o que garante isso: não existe
     * caminho para abrir uma sessão sem passar por aqui.
     */
    @Bean
    @Primary
    PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            TenantAwareInterceptor tenantAwareInterceptor) {
        TenantAwareTransactionManager transactionManager =
                new TenantAwareTransactionManager(tenantAwareInterceptor);
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
}
