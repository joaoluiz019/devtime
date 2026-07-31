package com.devtime.shared.ratelimit;

import com.devtime.shared.error.RateLimitExceededException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Contador de requisições em banco (§8.1 de {@code security.md}: "contador em banco no MVP").
 *
 * <p>Janela fixa: {@code window_started_at} marca o início do intervalo corrente e o contador
 * reinicia quando ele vence. Uma janela deslizante exigiria uma linha por tentativa, multiplicando
 * escritas justamente nos endpoints mais atacados.
 *
 * <p>A contagem e a decisão acontecem em <b>um único</b> {@code INSERT ... ON CONFLICT ...
 * RETURNING}, atômico no banco. Ler e depois escrever permitiria que requisições concorrentes
 * lessem o mesmo valor e ultrapassassem o limite — que é exatamente o cenário de um ataque
 * distribuído.
 *
 * <p>Transação <b>própria</b>, com {@code REQUIRES_NEW}: o contador precisa persistir mesmo quando
 * a requisição termina em erro. Participar da transação de negócio faria cada tentativa malsucedida
 * ser desfeita, tornando o limite inócuo contra força bruta — que é exatamente o caso de uso. A
 * transação explícita também é obrigatória porque o pool roda com {@code auto-commit = false}: sem
 * ela, o {@code INSERT} nunca chegaria a ser confirmado.
 */
@Component
public class RateLimiter {

    private static final String CONSUME_SQL =
            """
            INSERT INTO rate_limit_counters (bucket_key, window_started_at, hit_count)
                 VALUES (?, ?, 1)
            ON CONFLICT (bucket_key) DO UPDATE
                    SET hit_count = CASE
                            WHEN rate_limit_counters.window_started_at <= ? THEN 1
                            ELSE rate_limit_counters.hit_count + 1 END,
                        window_started_at = CASE
                            WHEN rate_limit_counters.window_started_at <= ? THEN ?
                            ELSE rate_limit_counters.window_started_at END
              RETURNING hit_count, window_started_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public RateLimiter(
            DataSource dataSource, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Registra uma tentativa e recusa quando o limite da janela é ultrapassado.
     *
     * @param discriminator valor que delimita o escopo — IP, e-mail, ou os dois combinados
     * @throws RateLimitExceededException {@code 429} com o tempo de espera restante
     */
    public void consume(RateLimitPolicy policy, String discriminator) {
        Instant now = clock.instant();
        Instant threshold = now.minus(policy.window());
        String key = bucketKey(policy, discriminator);

        Counter counter =
                transactionTemplate.execute(
                        status ->
                                jdbcTemplate.queryForObject(
                                        CONSUME_SQL,
                                        (rs, rowNum) ->
                                                new Counter(
                                                        rs.getInt("hit_count"),
                                                        rs.getObject(
                                                                        "window_started_at",
                                                                        OffsetDateTime.class)
                                                                .toInstant()),
                                        key,
                                        now.atOffset(ZoneOffset.UTC),
                                        threshold.atOffset(ZoneOffset.UTC),
                                        threshold.atOffset(ZoneOffset.UTC),
                                        now.atOffset(ZoneOffset.UTC)));

        if (counter != null && counter.hits() > policy.limit()) {
            Instant resetsAt = counter.windowStartedAt().plus(policy.window());
            Duration retryAfter = Duration.between(now, resetsAt);
            throw RateLimitExceededException.of(
                    policy.scope(), retryAfter.isNegative() ? Duration.ZERO : retryAfter);
        }
    }

    /**
     * Chave do contador.
     *
     * <p>O discriminador entra como SHA-256: os limites de login, redefinição e reenvio são por
     * e-mail, e guardá-lo em claro transformaria a tabela em uma lista de endereços cadastrados —
     * exatamente o dado que SG-01 a SG-03 impedem de obter pela API.
     */
    private String bucketKey(RateLimitPolicy policy, String discriminator) {
        String normalized = discriminator == null ? "" : discriminator.strip().toLowerCase();
        return policy.scope() + ":" + sha256(normalized);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    private record Counter(int hits, Instant windowStartedAt) {}
}
