package com.devtime.load;

import com.devtime.support.FeatureTestSupport;
import com.devtime.support.WorkLogScenario;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base dos testes de carga (T-008-43, T-010-24, T-012-35, T-015-29).
 *
 * <p>Ficam fora do ciclo padrão de {@code verify} — a marcação {@code carga} é excluída no
 * `pom.xml` e ativada por {@code -Pcarga}. Povoar 100.000 registros leva minutos, e um gate lento
 * em cada PR acaba desativado; separado, ele sobrevive.
 *
 * <p><b>Por que o povoamento não passa pelos serviços.</b> BR-207 exige que o dado de teste nasça
 * pelo caminho de produção, e é a regra certa para teste de regra de negócio: é o que impede um
 * {@code INSERT} de criar um estado que o sistema jamais produziria. Aqui a pergunta é outra —
 * "como o sistema se comporta com volume?" — e criar 100.000 registros pelo serviço mediria a
 * <b>escrita</b> um a um, que não é o que se quer medir, levando dezenas de minutos. Os valores
 * inseridos respeitam as mesmas invariantes que o serviço garantiria: sem sobreposição (RN-102),
 * {@code net_minutes > 0}, dentro do período e do mesmo tenant.
 */
@Tag("carga")
public abstract class LoadTestSupport extends FeatureTestSupport {

    /** Volume exigido por T-008-43 e T-010-24. */
    protected static final int VOLUME = 100_000;

    @Autowired private DataSource dataSource;

    protected JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Insere {@code quantidade} work logs no período, distribuídos pelos dias dele.
     *
     * <p>Cada registro ocupa uma janela de um minuto, e as janelas do mesmo dia não se tocam: é o
     * que mantém RN-102 verdadeira no conjunto gerado. Com mais registros do que minutos no dia, o
     * excedente vai para o dia seguinte do período.
     */
    protected void seedWorkLogs(WorkLogScenario.Scenario setup, UUID userId, int quantidade) {
        LocalDate inicio = setup.period().startDate();
        LocalDate fim = setup.period().endDate();
        int diasNoPeriodo = (int) (fim.toEpochDay() - inicio.toEpochDay()) + 1;
        // 1.380 janelas de um minuto por dia (00:00 a 23:00) deixam folga no fim do dia e evitam
        // qualquer encosto entre o último registro de um dia e o primeiro do seguinte.
        int porDia = 1_380;
        int porPessoa = diasNoPeriodo * porDia;

        // O período tem 22 dias e comporta ~30.000 registros por pessoa. O volume exigido vem de
        // várias pessoas, que é também a forma realista: RN-102 fala do mesmo usuário, e um tenant
        // com 100.000 registros num mês tem uma equipe, não alguém trabalhando 1.600 horas.
        int pessoas = (quantidade + porPessoa - 1) / porPessoa;
        List<UUID> usuarios = new ArrayList<>();
        usuarios.add(userId);
        for (int extra = 1; extra < pessoas; extra++) {
            usuarios.add(novoUsuario(extra));
        }

        List<Object[]> lote = new ArrayList<>(1_000);
        for (int indice = 0; indice < quantidade; indice++) {
            UUID dono = usuarios.get(indice / porPessoa);
            int posicao = indice % porPessoa;
            LocalDate dia = inicio.plusDays(posicao / porDia);
            int minutoDoDia = posicao % porDia;
            Instant comeco = WorkLogScenario.at(dia, minutoDoDia / 60, minutoDoDia % 60, 0);

            lote.add(
                    new Object[] {
                        UUID.randomUUID(),
                        tenantAId,
                        setup.ticket().id(),
                        setup.contract().id(),
                        setup.clientId(),
                        setup.period().id(),
                        dono,
                        setup.category().id(),
                        java.sql.Date.valueOf(dia),
                        java.sql.Timestamp.from(comeco),
                        java.sql.Timestamp.from(comeco.plus(Duration.ofMinutes(1))),
                        1,
                        1,
                        "Registro de carga " + indice,
                        indice % 5 != 0 // 80% faturável, como a distribuição real
                    });

            if (lote.size() == 1_000) {
                inserir(lote);
                lote.clear();
            }
        }
        if (!lote.isEmpty()) {
            inserir(lote);
        }
        // Conferir aqui, e não no teste: um povoamento que não persiste faz qualquer medição de
        // desempenho parecer excelente, e a falha se manifestaria como um p95 bom demais.
        long persistidas =
                jdbc().queryForObject(
                                "SELECT count(*) FROM work_logs WHERE tenant_id = ?",
                                Long.class,
                                tenantAId);
        System.out.println(
                "povoamento — pedidas="
                        + quantidade
                        + " gravadas="
                        + inseridas
                        + " persistidas="
                        + persistidas
                        + " pessoas="
                        + usuarios.size());
        if (persistidas < quantidade) {
            throw new IllegalStateException(
                    "o povoamento não persistiu: pedidas "
                            + quantidade
                            + ", encontradas "
                            + persistidas);
        }
    }

    /** Membro adicional do tenant A, dono de parte da massa. */
    private UUID novoUsuario(int indice) {
        return asOwnerOfA(
                () -> {
                    UUID id =
                            userRepository
                                    .save(
                                            com.devtime.support.FoundationDataBuilder.user(
                                                    "carga-"
                                                            + indice
                                                            + "-"
                                                            + UUID.randomUUID()
                                                                    .toString()
                                                                    .substring(0, 8)
                                                            + "@exemplo.com",
                                                    NOW))
                                    .getId();
                    membershipRepository.save(
                            com.devtime.support.FoundationDataBuilder.membership(
                                    tenantAId, id, com.devtime.shared.security.Role.MEMBER, NOW));
                    return id;
                });
    }

    /**
     * Grava um lote <b>dentro de uma transação</b>.
     *
     * <p>O detalhe não é cerimônia: {@code application.yml} configura o pool com {@code
     * auto-commit: false}, e um {@code JdbcTemplate} usado fora de transação obtém a conexão,
     * executa o {@code INSERT} e a devolve ao pool sem nunca confirmar — o {@code batchUpdate}
     * reporta 100.000 linhas afetadas e, um instante depois, a tabela está vazia. Foi exatamente o
     * que aconteceu aqui: o teste do painel media 66 ms de p95 "com 100.000 registros" sobre um
     * banco sem registro algum, e o resultado parecia ótimo justamente porque não media nada.
     */
    private void inserir(List<Object[]> lote) {
        int[] afetadas =
                inTransaction(
                        () ->
                                jdbc().batchUpdate(
                                                """
                        INSERT INTO work_logs (
                            id, tenant_id, ticket_id, contract_id, client_id, contract_period_id,
                            user_id, category_id, work_date, started_at, ended_at,
                            gross_minutes, net_minutes, description, billable, source)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL')
                        """,
                                                lote));
        inseridas += java.util.Arrays.stream(afetadas).filter(linhas -> linhas > 0).count();
    }

    /** Linhas efetivamente gravadas pelo povoamento, conferidas contra o que foi pedido. */
    private long inseridas;

    /** Percentil por interpolação de posição — o p95 de T-010-24 e de FR-166. */
    protected Duration percentil(List<Duration> amostras, int percentil) {
        List<Duration> ordenadas = new ArrayList<>(amostras);
        ordenadas.sort(Comparator.naturalOrder());
        int posicao = (int) Math.ceil(percentil / 100.0 * ordenadas.size()) - 1;
        return ordenadas.get(Math.clamp(posicao, 0, ordenadas.size() - 1));
    }

    /** Executa a operação {@code repeticoes} vezes e devolve a duração de cada uma. */
    protected List<Duration> medir(int repeticoes, Runnable operacao) {
        List<Duration> duracoes = new ArrayList<>(repeticoes);
        for (int volta = 0; volta < repeticoes; volta++) {
            Instant comeco = Instant.now();
            operacao.run();
            duracoes.add(Duration.between(comeco, Instant.now()));
        }
        return duracoes;
    }
}
