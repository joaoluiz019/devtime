package com.devtime.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base dos testes de integração.
 *
 * <p>ART-102 / BR-201/BR-202: banco PostgreSQL real via Testcontainers. H2 e bancos em memória são
 * <b>proibidos</b> (P-12) porque não suportam índices parciais, {@code EXCLUDE USING gist},
 * particionamento nem {@code JSONB} — exatamente os recursos em que as invariantes do DevTime se
 * apoiam. Um teste verde em H2 não provaria nada sobre produção.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestContainerConfiguration.class, FixedClockTestConfiguration.class})
@Tag("integration")
public abstract class IntegrationTestSupport {}
