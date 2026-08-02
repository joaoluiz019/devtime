package com.devtime.audit;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação das partições mensais de {@code audit_logs} (spec 002 §22.4, {@code AuditPartitionJob}).
 *
 * <p>V006 criou 12 partições explícitas e <b>nenhuma partição {@code DEFAULT}</b>. A ausência é
 * deliberada e é o que torna este serviço obrigatório: sem partição para o mês corrente, todo
 * {@code INSERT} na trilha falha — e uma trilha que para de gravar é uma falha crítica ({@code
 * audit.write.failures}, §29). Com uma partição {@code DEFAULT}, por outro lado, o PostgreSQL
 * recusaria anexar novas partições no intervalo já coberto por ela.
 *
 * <p>É a única classe do sistema que emite DDL em tempo de execução. Não há alternativa por JPA: o
 * comando é {@code CREATE TABLE ... PARTITION OF}, e Flyway não pode criar, a cada mês, uma
 * migration nova. O nome da tabela é construído a partir de um {@link YearMonth} — nunca de entrada
 * externa —, portanto não existe superfície de injeção (BR-168).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditPartitionService {

    /** Meses à frente mantidos sempre criados (spec §22.4). */
    static final int MONTHS_AHEAD = 3;

    private static final String PARTITION_PREFIX = "audit_logs_";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Garante as partições do mês corrente e dos {@value #MONTHS_AHEAD} seguintes.
     *
     * <p>BR-185: idempotente por {@code IF NOT EXISTS} — reexecutar no mesmo mês não produz efeito.
     *
     * @return quantidade de comandos executados
     */
    @Transactional
    public int ensurePartitions(LocalDate reference) {
        YearMonth start = YearMonth.from(reference);
        int created = 0;
        for (int offset = 0; offset <= MONTHS_AHEAD; offset++) {
            createIfAbsent(start.plusMonths(offset));
            created++;
        }
        return created;
    }

    private void createIfAbsent(YearMonth month) {
        String name =
                PARTITION_PREFIX
                        + month.getYear()
                        + "_"
                        + String.format("%02d", month.getMonthValue());
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + name
                        + " PARTITION OF audit_logs FOR VALUES FROM ('"
                        + from
                        + "') TO ('"
                        + to
                        + "')");
        log.debug("partição de auditoria garantida nome={}", name);
    }

    /** Instante de referência do job, no fuso UTC em que a coluna de particionamento é avaliada. */
    static LocalDate today(java.time.Clock clock) {
        // ART-030: audit_logs.occurred_at é TIMESTAMPTZ e o range das partições é comparado em UTC.
        // Usar o fuso do tenant aqui criaria bordas em que o mês do job difere do mês da partição.
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
