package com.devtime.shared.error;

import com.devtime.shared.observability.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Monta respostas de erro em RFC 7807 (ART-072, ADR-017 EX-02).
 *
 * <p>É o <b>único</b> lugar que produz corpo de erro. Centralizar aqui torna a garantia R-03 de
 * ADR-017 — nenhum stack trace, SQL, nome de tabela ou dado de outro tenant na resposta —
 * verificável em um ponto auditável, em vez de depender de disciplina espalhada por dezenas de
 * handlers.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailFactory {

    private static final String TYPE_PREFIX = "https://devtime.app/errors/";

    private final MessageSource messageSource;
    private final Clock clock;

    public ProblemDetail create(ErrorCode errorCode, HttpServletRequest request) {
        return create(errorCode, errorCode.getDefaultStatus(), Map.of(), request);
    }

    public ProblemDetail create(
            ErrorCode errorCode,
            HttpStatus status,
            Map<String, Object> details,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + slugOf(errorCode)));
        problem.setTitle(resolve(errorCode.getMessageKey() + ".title", status.getReasonPhrase()));
        // EX-10: a mensagem é apresentação; o code é o identificador estável.
        problem.setDetail(resolve(errorCode.getMessageKey(), status.getReasonPhrase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", errorCode.getCode());
        // EX-06: mesmo traceId do log e do trace distribuído.
        problem.setProperty("traceId", TraceContext.currentTraceId());
        problem.setProperty("timestamp", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        details.forEach(problem::setProperty);
        return problem;
    }

    /** Erro de validação de formato, com {@code errors[]} campo a campo. */
    public ProblemDetail validation(List<FieldError> errors, HttpServletRequest request) {
        ProblemDetail problem =
                create(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, Map.of(), request);
        problem.setProperty("errors", errors);
        return problem;
    }

    public ProblemDetail businessRule(BusinessRuleException exception, HttpServletRequest request) {
        return create(
                exception.getErrorCode(), exception.getStatus(), exception.getDetails(), request);
    }

    private String resolve(String key, String fallback) {
        return messageSource.getMessage(key, null, fallback, LocaleContextHolder.getLocale());
    }

    /**
     * Converte {@code RESOURCE_NOT_FOUND} em {@code resource-not-found} para o campo {@code type}.
     */
    private String slugOf(ErrorCode errorCode) {
        return errorCode.name().toLowerCase().replace('_', '-');
    }
}
