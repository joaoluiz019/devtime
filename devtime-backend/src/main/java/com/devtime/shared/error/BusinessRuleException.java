package com.devtime.shared.error;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Raiz das exceções de regra de negócio (backend.md §8.3, ADR-017 EX-07).
 *
 * <p>Toda instância é criada por <b>método fábrica nomeado pela regra</b>, nunca por construtor
 * genérico a partir de código cliente: é o que garante que código, mensagem e detalhes permaneçam
 * coerentes entre si (BR-063). Por isso o construtor é {@code protected}.
 *
 * <p>ADR-017 EX-07 prevê que esta hierarquia seja {@code sealed}. Ela ainda não é: em Java, uma
 * classe {@code sealed} exige ao menos um subtipo permitido, e nesta sprint existe apenas {@link
 * EntityNotFoundException}. O selamento acompanha a introdução dos subtipos de domínio (T-001-34 em
 * diante), que é quando a cláusula {@code permits} passa a ter valor de verificação.
 */
@Getter
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    /** Contexto adicional exposto na resposta. Nunca contém dado sensível nem de outro tenant. */
    private final transient Map<String, Object> details;

    protected BusinessRuleException(
            ErrorCode errorCode, HttpStatus status, Map<String, Object> details, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    protected BusinessRuleException(
            ErrorCode errorCode, Map<String, Object> details, String message) {
        this(errorCode, errorCode.getDefaultStatus(), details, message);
    }

    /**
     * RN-004 / ART-052: edição concorrente perdeu a corrida de versão.
     *
     * <p>Fábrica em vez de mapeamento direto de {@code OptimisticLockException} porque o serviço
     * pode detectar o conflito antes de o Hibernate detectá-lo, e as duas origens devem produzir a
     * mesma resposta.
     */
    public static BusinessRuleException versionConflict(String entityType, long expectedVersion) {
        return new BusinessRuleException(
                ErrorCode.VERSION_CONFLICT,
                Map.of("entityType", entityType, "expectedVersion", expectedVersion),
                "Conflito de versão em " + entityType);
    }

    /** RN-011: tentativa de alterar campo marcado como imutável em entities.md. */
    public static BusinessRuleException immutableField(String field) {
        return new BusinessRuleException(
                ErrorCode.IMMUTABLE_FIELD, Map.of("field", field), "Campo imutável: " + field);
    }

    /** RN-012 / ART-073: {@code size} solicitado acima do máximo de 100. */
    public static BusinessRuleException pageSizeExceeded(int requestedSize, int maxSize) {
        return new BusinessRuleException(
                ErrorCode.PAGE_SIZE_EXCEEDED,
                Map.of("requestedSize", requestedSize, "maxSize", maxSize),
                "Tamanho de página acima do máximo permitido");
    }
}
