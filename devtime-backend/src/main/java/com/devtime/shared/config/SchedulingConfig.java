package com.devtime.shared.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agendamento de jobs com lock distribuído (backend.md §11).
 *
 * <p>JB-07: jobs executam apenas no perfil {@code scheduler}. Sem esse recorte, cada réplica da
 * aplicação tentaria executar todo job, e o desenvolvimento local disputaria locks com o ambiente
 * compartilhado.
 *
 * <p>JB-01: o {@link LockProvider} garante que apenas uma instância execute cada job. Nenhum job
 * existe nesta sprint — a infraestrutura é entregue porque a tabela {@code shedlock} faz parte da
 * migration inicial (V007) e o provedor precisa existir para que ela seja validada.
 */
@Configuration
@Profile("scheduler")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(
                                new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .withTableName("shedlock")
                        // Sem isto, o lock usaria o relógio da JVM; com o relógio do banco,
                        // réplicas
                        // com horários levemente diferentes não conseguem burlar o lock.
                        .usingDbTime()
                        .build());
    }
}
