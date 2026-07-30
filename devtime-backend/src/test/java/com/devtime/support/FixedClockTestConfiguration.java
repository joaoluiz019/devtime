package com.devtime.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Relógio fixo para os testes.
 *
 * <p>BR-205: nenhum teste usa relógio real. Com um instante fixo, asserções sobre {@code
 * createdAt}, {@code exp} de token e cálculo de data passam a ser igualdades exatas em vez de
 * intervalos tolerantes — o que elimina a classe de teste instável que falha uma vez por mês na
 * virada de dia.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockTestConfiguration {

    /** Instante de referência: 29/07/2026 14:32:10 UTC. */
    public static final Instant FIXED_INSTANT = Instant.parse("2026-07-29T14:32:10Z");

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
}
