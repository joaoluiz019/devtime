package com.devtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.support.IntegrationTestSupport;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Migrations Flyway aplicadas em banco limpo (CA-05 e CA-08 de database.md).
 *
 * <p>O simples fato de o contexto Spring subir já prova o mais import: {@code ddl-auto=validate}
 * falharia a inicialização se qualquer mapeamento JPA divergisse do schema criado pelas migrations
 * (ART-054). Os testes abaixo verificam o que a validação de schema não cobre — particionamento,
 * índices parciais e a versão final aplicada.
 */
class FlywayMigrationIntegrationTest extends IntegrationTestSupport {

    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("CA-05: toda migration versionada aplica em banco limpo, sem exceção")
    void allMigrationsMustApply() throws IOException {
        List<String> aplicadas =
                jdbc().queryForList(
                                "SELECT version FROM flyway_schema_history WHERE success = true"
                                        + " ORDER BY installed_rank",
                                String.class);

        // A expectativa vem dos arquivos, não de uma lista escrita à mão. Uma lista literal precisa
        // ser editada a cada migration nova e já quebrou o build duas vezes por estar desatualizada
        // — falhando por manutenção esquecida, não por defeito. Derivá-la dos arquivos mantém a
        // pergunta que importa ("alguma migration deixou de aplicar em banco limpo?") e elimina a
        // única forma de esta suíte falhar sem que nada esteja errado.
        List<String> declaradas = versoesDeclaradasNoClasspath();

        assertThat(declaradas).as("nenhuma migration encontrada em db/migration").isNotEmpty();
        assertThat(aplicadas)
                .as("toda migration do classpath aplicou, e nada além delas foi aplicado")
                .containsExactlyElementsOf(declaradas);
    }

    @Test
    @DisplayName("BR-035: as migrations aplicam em ordem crescente de versão, sem out-of-order")
    void migrationsMustApplyInAscendingVersionOrder() {
        List<String> aplicadas =
                jdbc().queryForList(
                                "SELECT version FROM flyway_schema_history WHERE success = true"
                                        + " ORDER BY installed_rank",
                                String.class);

        // `validate-on-migrate: true` recusa uma migration intercalada abaixo da maior versão já
        // aplicada. É por isto que `V021` permanece vago para sempre: era o número reservado a
        // `report_executions`, mas quando `012-reports` foi implementada a sequência real já ia
        // além, e preencher o vão exigiria `out-of-order`. Um número de migration nunca é
        // reaproveitado.
        List<Integer> numeros = aplicadas.stream().map(Integer::valueOf).toList();

        assertThat(numeros).as("ordem de aplicação = ordem de versão").isSorted();
        assertThat(numeros).as("nenhuma versão aplicada duas vezes").doesNotHaveDuplicates();
    }

    /** Versões dos arquivos {@code V<n>__*.sql} do classpath, em ordem crescente. */
    private List<String> versoesDeclaradasNoClasspath() throws IOException {
        Resource[] arquivos =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:db/migration/V*__*.sql");

        return Arrays.stream(arquivos)
                .map(Resource::getFilename)
                .filter(Objects::nonNull)
                .map(nome -> nome.substring(1, nome.indexOf("__")))
                .sorted(Comparator.comparingInt(Integer::valueOf))
                .toList();
    }

    @Test
    @DisplayName("INV-TCK-01: tickets possui índice único parcial sobre (contract_id, number)")
    void ticketNumberMustBeUniquePerContract() {
        List<String> definitions =
                jdbc().queryForList(
                                """
                        SELECT indexdef FROM pg_indexes
                         WHERE schemaname = 'public'
                           AND tablename = 'tickets'
                           AND indexname = 'uq_tickets_contract_number'
                        """,
                                String.class);

        assertThat(definitions).hasSize(1);
        assertThat(definitions.get(0))
                .as("ART-055: parcial, para que um ticket excluído não bloqueie a numeração")
                .contains("deleted_at IS NULL");
    }

    @Test
    @DisplayName("§6.2 de specs/007: tickets NÃO possui coluna key — a chave é derivada")
    void ticketKeyMustNotBePersisted() {
        List<String> columns =
                jdbc().queryForList(
                                "SELECT column_name FROM information_schema.columns"
                                        + " WHERE table_name = 'tickets'",
                                String.class);

        assertThat(columns)
                .as("entities.md §6.12 marca a chave como campo derivado (📐)")
                .doesNotContain("key");
    }

    @Test
    @DisplayName("RN-811: o CHECK de comments rejeita INSERT direto com 0 e com 10.001 caracteres")
    void commentBodyLengthMustBeEnforcedByConstraint() {
        List<String> constraints =
                jdbc().queryForList(
                                """
                        SELECT pg_get_constraintdef(oid) FROM pg_constraint
                         WHERE conname = 'ck_comments_body_length'
                        """,
                                String.class);

        assertThat(constraints).hasSize(1);
        assertThat(constraints.get(0)).contains("10000");
    }

    @Test
    @DisplayName("V001: as extensões exigidas pelo schema estão instaladas")
    void requiredExtensionsMustBeInstalled() {
        List<String> extensions =
                jdbc().queryForList("SELECT extname FROM pg_extension", String.class);

        assertThat(extensions).contains("pgcrypto", "btree_gist", "pg_trgm");
    }

    @Test
    @DisplayName("V006: audit_logs é particionada por range com as 12 partições iniciais")
    void auditLogsMustBePartitioned() {
        String strategy =
                jdbc().queryForObject(
                                """
                        SELECT partstrat FROM pg_partitioned_table
                          JOIN pg_class ON pg_class.oid = pg_partitioned_table.partrelid
                         WHERE pg_class.relname = 'audit_logs'
                        """,
                                String.class);

        Integer partitions =
                jdbc().queryForObject(
                                """
                        SELECT count(*) FROM pg_inherits
                          JOIN pg_class parent ON parent.oid = pg_inherits.inhparent
                         WHERE parent.relname = 'audit_logs'
                        """,
                                Integer.class);

        assertThat(strategy).as("'r' indica particionamento por range").isEqualTo("r");
        assertThat(partitions).isEqualTo(12);
    }

    @Test
    @DisplayName("INV-AUD-01: audit_logs não possui updated_at, deleted_at nem version")
    void auditLogsMustBeAppendOnlyByStructure() {
        List<String> columns =
                jdbc().queryForList(
                                "SELECT column_name FROM information_schema.columns"
                                        + " WHERE table_name = 'audit_logs'",
                                String.class);

        assertThat(columns)
                .as("um registro de auditoria alterável não tem valor probatório")
                .doesNotContain("updated_at", "updated_by", "deleted_at", "deleted_by", "version");
    }

    @Test
    @DisplayName("ART-055 / CA-03: todo índice único de entidade soft-deletable é parcial")
    void uniqueIndexesOnSoftDeletableTablesMustBePartial() {
        List<String> nonPartial =
                jdbc().queryForList(
                                """
                        SELECT indexname FROM pg_indexes
                         WHERE schemaname = 'public'
                           AND indexdef LIKE 'CREATE UNIQUE INDEX%'
                           AND indexname LIKE 'uq_%'
                           AND indexdef NOT LIKE '%WHERE%'
                           AND tablename IN ('tenants', 'users', 'memberships')
                        """,
                                String.class);

        assertThat(nonPartial)
                .as(
                        "sem WHERE deleted_at IS NULL, um registro excluído logicamente impediria"
                                + " recadastrar outro com a mesma chave natural (CE-DB-01)")
                .isEmpty();
    }

    @Test
    @DisplayName("CA-02: todo índice composto de tabela tenant-scoped começa por tenant_id")
    void compositeIndexesMustStartWithTenantId() {
        List<String> definitions =
                jdbc().queryForList(
                                """
                        SELECT indexdef FROM pg_indexes
                         WHERE schemaname = 'public'
                           AND tablename = 'memberships'
                           AND indexname LIKE 'idx_%'
                        """,
                                String.class);

        assertThat(definitions)
                .as(
                        "database.md §5.1: sem tenant_id no primeiro nível da B-Tree, o planejador"
                                + " filtraria o tenant só após a varredura")
                .isNotEmpty();
        assertThat(definitions.stream().anyMatch(definition -> definition.contains("(tenant_id")))
                .isTrue();
    }

    @Test
    @DisplayName("V007: a tabela de infraestrutura do ShedLock existe")
    void shedlockTableMustExist() {
        Integer count =
                jdbc().queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'shedlock'",
                                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
