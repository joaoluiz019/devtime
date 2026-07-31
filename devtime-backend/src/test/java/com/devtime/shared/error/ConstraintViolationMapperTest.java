package com.devtime.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Mapeamento de violação de constraint por nome (ADR-017 EX-08).
 *
 * <p>Este mapeamento é o que torna a convenção de nomes das migrations parte do contrato de erro:
 * sem ele, a única alternativa seria expor a mensagem do banco, que contém nome de tabela, coluna e
 * valor — proibido por R-03 de ADR-017.
 */
class ConstraintViolationMapperTest {

    private final ConstraintViolationMapper mapper = new ConstraintViolationMapper();

    @Test
    @DisplayName("entities.md §11: índice único violado vira DEVTIME-2001 / 409")
    void uniqueConstraintMustMapToUniquenessViolation() {
        var exception = wrap("uq_clients_tenant_document");

        assertThat(mapper.map(exception)).contains(ErrorCode.UNIQUENESS_VIOLATION);
    }

    @Test
    @DisplayName(
            "CX-02 / EX-13: uq_users_email tem código próprio, mais específico que a convenção")
    void userEmailConstraintMustMapToDedicatedCode() {
        var exception = wrap("uq_users_email");

        assertThat(mapper.map(exception))
                .as(
                        "dois cadastros simultâneos precisam da mesma resposta do caminho verificado"
                                + " antes da inserção (AC-001-40)")
                .contains(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("database.md §13: FK violada vira DEVTIME-2002 / 404, nunca 403 (ART-024)")
    void foreignKeyConstraintMustMapToNotFound() {
        var exception = wrap("fk_memberships_tenants");

        assertThat(mapper.map(exception))
                .as("FK inexistente e FK de outro tenant são indistinguíveis para o cliente")
                .contains(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("EX-04: constraint CHECK sem código de regra não recebe mapeamento")
    void checkConstraintMustNotBeMappedWithoutRule() {
        var exception = wrap("ck_users_status");

        assertThat(mapper.map(exception))
                .as("§10 da constituição: constraint não mapeada resulta em 500 DEVTIME-9001")
                .isEmpty();
    }

    @Test
    @DisplayName("EX-04: constraint EXCLUDE sem código de regra não recebe mapeamento")
    void excludeConstraintMustNotBeMappedWithoutRule() {
        assertThat(mapper.map(wrap("ex_periods_no_overlap"))).isEmpty();
    }

    @Test
    @DisplayName("O nome da constraint é recuperável para log, mas nunca mapeado automaticamente")
    void constraintNameMustBeAvailableForLogging() {
        assertThat(mapper.constraintNameOf(wrap("uq_tenants_slug"))).contains("uq_tenants_slug");
    }

    @Test
    @DisplayName("Exceção sem constraint identificável não produz mapeamento")
    void exceptionWithoutConstraintMustReturnEmpty() {
        var exception = new DataIntegrityViolationException("falha genérica");

        assertThat(mapper.constraintNameOf(exception)).isEmpty();
        assertThat(mapper.map(exception)).isEmpty();
    }

    /** Reproduz o encadeamento real: Spring embrulha a exceção de Hibernate. */
    private DataIntegrityViolationException wrap(String constraintName) {
        var hibernateException =
                new ConstraintViolationException(
                        "violação", new SQLException("detalhe do banco"), constraintName);
        return new DataIntegrityViolationException("violação de integridade", hibernateException);
    }
}
