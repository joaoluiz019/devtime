package com.devtime.shared.tenancy;

import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.error.ProblemDetailFactory;
import com.devtime.shared.observability.TraceContext;
import com.devtime.shared.security.AccessTokenClaims;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.SessionValidationService.Decision;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Popula o {@link TenantContext} a partir das claims do access token (backend.md §7.2).
 *
 * <p>Camada 1 da defesa em profundidade de {@code security.md} §6.1: o {@code tenantId} vem
 * exclusivamente da claim {@code tid}. Qualquer {@code tenantId} presente em body, query, path ou
 * header é <b>ignorado</b> (ART-021, TI-01, BR-041). O frontend envia {@code X-Tenant-Id} apenas
 * para correlação de logs, e este filtro deliberadamente não o lê.
 *
 * <p>As permissões são derivadas do papel <b>aqui, a cada requisição</b>, nunca lidas do token
 * (TK-03, AZ-02, BR-163): assim, o rebaixamento de um {@code ADMIN} a {@code MEMBER} tem efeito
 * imediato, em vez de esperar os 15 minutos de validade do token.
 *
 * <p>Aplica também os passos 3 e 4 de {@code permissions.md} §4.1 — situação da organização e do
 * vínculo — por meio de {@link SessionValidationService} (T-001-14). Sem eles, um membro removido
 * continuaria operando até o access token expirar (CE-AU-07, CE-P-09).
 */
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    /** Métodos sem efeito de escrita, liberados em organização suspensa (RN-007). */
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * Prefixo alcançável com token de pré-seleção (CE-P-11).
     *
     * <p>É todo o {@code /api/v1/auth}: listar organizações, selecionar uma, renovar e encerrar a
     * sessão precisam funcionar antes de existir organização escolhida. Qualquer outro caminho é
     * recusado com {@code 401 DEVTIME-1002}.
     */
    private static final String TENANT_OPTIONAL_PREFIX = "/api/v1/auth";

    private final TenantContext tenantContext;
    private final SessionValidationService sessionValidationService;
    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AccessTokenClaims claims = authenticatedClaims();
        try {
            if (claims != null) {
                ErrorCode rejection = rejectionFor(request, claims);
                if (rejection != null) {
                    writeProblem(request, response, rejection);
                    return;
                }
                tenantContext.set(toSession(claims));
                publishToLogContext(claims);
            }
            chain.doFilter(request, response);
        } finally {
            // Obrigatório: sem esta limpeza, uma thread reutilizada do pool atenderia a próxima
            // requisição com o tenant da anterior — vazamento entre tenants.
            tenantContext.clear();
            clearLogContext();
        }
    }

    /**
     * @return o código a devolver, ou {@code null} quando a requisição pode prosseguir
     */
    private ErrorCode rejectionFor(HttpServletRequest request, AccessTokenClaims claims) {
        if (claims.tenantId() == null) {
            // Passo 2 de permissions.md §4.1: tenant selecionado. Precede a verificação de
            // permissão
            // (passo 5) — sem isto, um token de pré-seleção receberia 403 por não ter permissão
            // alguma, e o cliente concluiria "acesso negado" quando o certo é "escolha uma
            // organização" (CE-P-11, AC-001-21).
            return request.getRequestURI().startsWith(TENANT_OPTIONAL_PREFIX)
                    ? null
                    : ErrorCode.TENANT_NOT_SELECTED;
        }
        Decision decision =
                sessionValidationService.validate(
                        claims.tenantId(), claims.userId(), claims.issuedAt());
        return switch (decision) {
            case ALLOWED -> null;
            case TENANT_CANCELLED -> ErrorCode.TENANT_CANCELLED;
            case MEMBERSHIP_INACTIVE -> ErrorCode.MEMBERSHIP_INACTIVE;
            // TK-05: o token perdeu validade porque o papel mudou. Responde como autenticação
            // necessária para que o cliente renove e receba o papel novo, em vez de tratar como
            // permissão negada — que sugeriria ao usuário que ele fez algo indevido.
            case TOKEN_STALE -> ErrorCode.AUTHENTICATION_REQUIRED;
            case TENANT_READ_ONLY ->
                    READ_METHODS.contains(request.getMethod())
                            ? null
                            : ErrorCode.TENANT_SUSPENDED; // RN-007
        };
    }

    private void writeProblem(
            HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        ProblemDetail problem = problemDetailFactory.create(errorCode, request);
        response.setStatus(errorCode.getDefaultStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store"); // security.md §8.2
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private TenantSession toSession(AccessTokenClaims claims) {
        return new TenantSession(
                claims.userId(),
                claims.tenantId(),
                claims.membershipId(),
                claims.role(),
                RolePermissions.of(claims.role()),
                claims.timezone());
    }

    private AccessTokenClaims authenticatedClaims() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof AccessTokenClaims claims ? claims : null;
    }

    /** architecture.md §12: todo log estruturado carrega tenantId e userId. */
    private void publishToLogContext(AccessTokenClaims claims) {
        MDC.put(TraceContext.USER_ID, claims.userId().toString());
        if (claims.tenantId() != null) {
            MDC.put(TraceContext.TENANT_ID, claims.tenantId().toString());
        }
    }

    private void clearLogContext() {
        MDC.remove(TraceContext.USER_ID);
        MDC.remove(TraceContext.TENANT_ID);
    }
}
