package com.devtime.shared.error;

import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

/**
 * Traduz violação de constraint do banco em código de negócio (ADR-017 EX-08).
 *
 * <p>A mensagem de {@code DataIntegrityViolationException} contém nome de tabela, coluna e valor —
 * tudo proibido na resposta (R-03 de ADR-017). Mapear pelo <b>nome da constraint</b> produz erro
 * útil sem vazar estrutura, o que torna a convenção de nomenclatura das migrations parte do
 * contrato de erro (database.md §4.1).
 *
 * <p>O mapeamento é por prefixo porque a convenção de nomes já codifica a natureza da constraint.
 * Constraint não reconhecida resulta em {@code null} e, por EX-04 e §10 da constituição, o chamador
 * a trata como {@code 500 DEVTIME-9001} — fechar por padrão, nunca adivinhar um código de negócio.
 */
@Component
public class ConstraintViolationMapper {

    /**
     * @return o código correspondente, ou vazio quando a constraint não é reconhecida
     */
    public Optional<ErrorCode> map(Throwable throwable) {
        return constraintNameOf(throwable).flatMap(this::byNamingConvention);
    }

    /** Nome da constraint violada, para registro em log — <b>nunca</b> para a resposta. */
    public Optional<String> constraintNameOf(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return Optional.of(violation.getConstraintName());
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    /**
     * Constraints com código próprio, mais específico que a convenção de prefixo (EX-13).
     *
     * <p>{@code uq_users_email} é o caso de CX-02 / AC-001-40: dois cadastros simultâneos com o
     * mesmo e-mail. Um deles perde a corrida no índice, e a resposta precisa ser exatamente a mesma
     * do caminho verificado antes da inserção — {@code DEVTIME-2452} —, não o {@code DEVTIME-2001}
     * genérico. Caso contrário, o cliente distinguiria "e-mail duplicado" de "corrida perdida",
     * revelando concorrência de cadastro sobre o mesmo endereço.
     */
    private static final java.util.Map<String, ErrorCode> BY_CONSTRAINT =
            java.util.Map.of("uq_users_email", ErrorCode.EMAIL_ALREADY_REGISTERED);

    private Optional<ErrorCode> byNamingConvention(String constraintName) {
        String name = constraintName.toLowerCase();
        ErrorCode specific = BY_CONSTRAINT.get(name);
        if (specific != null) {
            return Optional.of(specific);
        }
        if (name.startsWith("uq_")) {
            // entities.md §11: violação de unicidade → DEVTIME-2001 / 409.
            return Optional.of(ErrorCode.UNIQUENESS_VIOLATION);
        }
        if (name.startsWith("fk_")) {
            // database.md §13: FK inexistente ou de outro tenant → 404 DEVTIME-2002 (ART-024).
            return Optional.of(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // ck_ e ex_ exigem o código da regra específica, que pertence à feature que a introduz
        // (EX-13). Sem mapeamento explícito, o chamador fecha com DEVTIME-9001.
        return Optional.empty();
    }
}
