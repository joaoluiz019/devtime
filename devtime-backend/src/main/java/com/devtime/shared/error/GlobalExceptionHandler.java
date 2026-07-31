package com.devtime.shared.error;

import com.devtime.shared.observability.TraceContext;
import com.devtime.shared.security.InvalidAccessTokenException;
import com.devtime.shared.tenancy.CrossTenantWriteException;
import com.devtime.shared.tenancy.TenantContextNotInitializedException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Tradução global de exceções em Problem Details (backend.md §12, ADR-017 EX-01).
 *
 * <p>O mapeamento segue a tabela canônica de ADR-017 e é <b>exaustivo</b>: o handler de {@code
 * Exception} garante EX-04 — nenhuma exceção escapa sem tradução, e o padrão é {@code 500
 * DEVTIME-9001}.
 *
 * <p>EX-12 define os níveis de log: {@code 4xx} em {@code WARN} sem stack trace, porque não é falha
 * do sistema; {@code 5xx} em {@code ERROR} com a exceção completa, porque é.
 */
@RestControllerAdvice
// Precedência máxima: nenhum outro advice — incluindo os registrados por auto-configuração — pode
// responder a uma exceção antes deste e produzir um corpo sem `code` e `traceId` (ART-072).
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ProblemDetailFactory factory;
    private final ConstraintViolationMapper constraintViolationMapper;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidArgument(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldError> errors =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(field -> new FieldError(field.getField(), field.getDefaultMessage()))
                        .toList();
        logClientError(ErrorCode.VALIDATION_FAILED, request, exception);
        return factory.validation(errors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        List<FieldError> errors =
                exception.getAllErrors().stream()
                        .map(error -> new FieldError(errorField(error), error.getDefaultMessage()))
                        .toList();
        logClientError(ErrorCode.VALIDATION_FAILED, request, exception);
        return factory.validation(errors, request);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ProblemDetail handleBeanValidation(
            jakarta.validation.ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldError> errors =
                exception.getConstraintViolations().stream()
                        .map(violation -> new FieldError(pathOf(violation), violation.getMessage()))
                        .toList();
        logClientError(ErrorCode.VALIDATION_FAILED, request, exception);
        return factory.validation(errors, request);
    }

    /**
     * JSON malformado.
     *
     * <p>A mensagem do parser nunca é exposta: ela cita nomes de classe e posições do payload, o
     * que revela estrutura interna (R-03 de ADR-017).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        logClientError(ErrorCode.VALIDATION_FAILED, request, exception);
        return factory.create(
                ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, Map.of(), request);
    }

    @ExceptionHandler({AuthenticationException.class, InvalidAccessTokenException.class})
    ProblemDetail handleAuthentication(Exception exception, HttpServletRequest request) {
        logClientError(ErrorCode.AUTHENTICATION_REQUIRED, request, exception);
        return factory.create(ErrorCode.AUTHENTICATION_REQUIRED, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        // IMP-05: toda negação é registrada, para detectar tentativa de escalonamento.
        log.warn(
                "Acesso negado. code={} path={} tenantId={} userId={} traceId={}",
                ErrorCode.PERMISSION_DENIED.getCode(),
                request.getRequestURI(),
                mdc(TraceContext.TENANT_ID),
                mdc(TraceContext.USER_ID),
                TraceContext.currentTraceId());
        return factory.create(ErrorCode.PERMISSION_DENIED, request);
    }

    /**
     * Limite de requisições excedido (ART-073).
     *
     * <p>Único handler que devolve {@code ResponseEntity} em vez de {@code ProblemDetail}: §4.6 e
     * §8.1 de {@code security.md} exigem o header {@code Retry-After}, e ele não é expressável no
     * corpo. Precede {@link #handleBusinessRule} por ser subtipo mais específico.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    org.springframework.http.ResponseEntity<ProblemDetail> handleRateLimit(
            RateLimitExceededException exception, HttpServletRequest request) {
        logClientError(exception.getErrorCode(), request, exception);
        return org.springframework.http.ResponseEntity.status(
                        exception.getErrorCode().getDefaultStatus())
                .header(
                        org.springframework.http.HttpHeaders.RETRY_AFTER,
                        String.valueOf(exception.getRetryAfter().toSeconds()))
                .body(factory.businessRule(exception, request));
    }

    /**
     * Exceções de negócio, incluindo {@link EntityNotFoundException}.
     *
     * <p>Um único handler porque cada exceção já carrega seu código e status; ramificar por subtipo
     * apenas duplicaria a decisão que a fábrica da exceção já tomou (BR-063).
     */
    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException exception, HttpServletRequest request) {
        logClientError(exception.getErrorCode(), request, exception);
        return factory.businessRule(exception, request);
    }

    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    ProblemDetail handleOptimisticLock(Exception exception, HttpServletRequest request) {
        logClientError(ErrorCode.VERSION_CONFLICT, request, exception);
        return factory.create(ErrorCode.VERSION_CONFLICT, request);
    }

    /**
     * Violação de integridade do banco (EH-05, EX-08).
     *
     * <p>Constraint não reconhecida cai em {@code 500 DEVTIME-9001} conforme §10 da constituição —
     * o nome da constraint vai para o log, nunca para a resposta.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        String constraintName =
                constraintViolationMapper.constraintNameOf(exception).orElse("desconhecida");
        return constraintViolationMapper
                .map(exception)
                .map(
                        errorCode -> {
                            log.warn(
                                    "Violação de integridade mapeada. code={} constraint={} path={}"
                                            + " traceId={}",
                                    errorCode.getCode(),
                                    constraintName,
                                    request.getRequestURI(),
                                    TraceContext.currentTraceId());
                            return factory.create(errorCode, request);
                        })
                .orElseGet(
                        () -> {
                            log.error(
                                    "Violação de constraint não mapeada. constraint={} path={}"
                                            + " traceId={}",
                                    constraintName,
                                    request.getRequestURI(),
                                    TraceContext.currentTraceId(),
                                    exception);
                            return factory.create(ErrorCode.UNEXPECTED, request);
                        });
    }

    /**
     * Falhas de tenancy.
     *
     * <p>§10 da constituição e §14 de security.md classificam ambas como {@code ERROR} com alerta
     * crítico: contexto vazio em requisição autenticada é defeito, e escrita cross-tenant é
     * tentativa de violação de isolamento. A resposta é genérica para não informar ao atacante o
     * que foi detectado.
     */
    @ExceptionHandler({TenantContextNotInitializedException.class, CrossTenantWriteException.class})
    ProblemDetail handleTenancyFailure(RuntimeException exception, HttpServletRequest request) {
        if (exception instanceof TenantContextNotInitializedException) {
            // CE-P-11: é o token de pré-seleção alcançando um endpoint de negócio. Não é falha
            // interna, e sim o estado previsto de "organização ainda não escolhida" — responder 500
            // faria o cliente tratar como indisponibilidade em vez de redirecionar para a seleção.
            log.info(
                    "Requisição sem organização selecionada. path={} traceId={}",
                    request.getRequestURI(),
                    TraceContext.currentTraceId());
            return factory.create(ErrorCode.TENANT_NOT_SELECTED, request);
        }
        // CrossTenantWriteException é outra coisa: uma escrita tentou gravar em tenant diferente do
        // contexto. Isso é defeito ou ataque, nunca fluxo previsto (security.md §6.1, camada 3).
        log.error(
                "Falha de isolamento de tenant. path={} traceId={}",
                request.getRequestURI(),
                TraceContext.currentTraceId(),
                exception);
        return factory.create(ErrorCode.UNEXPECTED, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error(
                "Erro inesperado. code={} path={} traceId={}",
                ErrorCode.UNEXPECTED.getCode(),
                request.getRequestURI(),
                TraceContext.currentTraceId(),
                exception);
        return factory.create(ErrorCode.UNEXPECTED, request);
    }

    /** EX-12: {@code 4xx} em WARN, sem stack trace — não é falha do sistema. */
    private void logClientError(ErrorCode errorCode, HttpServletRequest request, Exception cause) {
        log.warn(
                "Requisição rejeitada. code={} status={} path={} traceId={} reason={}",
                errorCode.getCode(),
                errorCode.getDefaultStatus().value(),
                request.getRequestURI(),
                TraceContext.currentTraceId(),
                cause.getClass().getSimpleName());
    }

    private String pathOf(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
    }

    private String errorField(org.springframework.context.MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        return codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
    }

    private String mdc(String key) {
        String value = org.slf4j.MDC.get(key);
        return value == null ? "" : value;
    }
}
