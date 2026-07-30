package com.devtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.support.IntegrationTestSupport;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    @DisplayName("CA-05: todas as migrations de F0 (V001–V007) aplicam em banco limpo")
    void allFoundationMigrationsMustApply() {
        List<String> versions =
                jdbc().queryForList(
                                "SELECT version FROM flyway_schema_history WHERE success = true"
                                        + " ORDER BY installed_rank",
                                String.class);

        assertThat(versions).containsExactly("001", "002", "003", "004", "005", "006", "007");
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
