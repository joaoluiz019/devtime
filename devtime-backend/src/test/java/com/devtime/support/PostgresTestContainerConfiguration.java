package com.devtime.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Contêiner PostgreSQL usado por todos os testes de integração.
 *
 * <p>A versão é fixada em 16 (architecture.md §11 e coding-guidelines.md RE-03) e não em {@code
 * latest}: o schema depende de recursos cujo comportamento varia entre versões maiores — índice
 * parcial com {@code INCLUDE}, {@code EXCLUDE USING gist} e particionamento por range.
 *
 * <p>O contêiner é {@code static} para ser reutilizado entre classes de teste. Sem isso, cada
 * classe pagaria a inicialização do PostgreSQL, e a suíte de integração deixaria de ser executável
 * a cada commit — o que na prática levaria a executá-la menos.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfiguration {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("devtime_test")
                .withUsername("devtime")
                .withPassword("devtime")
                .withReuse(true);
    }
}
