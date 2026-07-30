package com.devtime.shared.tenancy;

import com.devtime.shared.observability.TraceContext;
import com.devtime.shared.security.AccessTokenClaims;
import com.devtime.shared.security.RolePermissions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
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
 * <p><b>Fora do escopo desta sprint:</b> a verificação de status do tenant e de membership ativo
 * (passos 3 e 4 de {@code permissions.md} §4.1) exige consultar o banco, o que faria {@code shared}
 * depender de uma feature e violar AR-01/BR-004. Essa verificação pertence à feature 001 (tarefa
 * T-001-14), que introduz os serviços correspondentes. Até então, um membership revogado continua
 * autenticado até o access token expirar.
 */
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AccessTokenClaims claims = authenticatedClaims();
        try {
            if (claims != null) {
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
