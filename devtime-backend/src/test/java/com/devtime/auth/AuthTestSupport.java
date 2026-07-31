package com.devtime.auth;

import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.support.IntegrationTestSupport;
import com.devtime.tenant.MembershipRepository;
import com.devtime.tenant.TenantRepository;
import com.devtime.user.UserRepository;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base dos testes de integração da feature 001.
 *
 * <p>Diferente de {@code FeatureTestSupport}, <b>não</b> cria tenants nem usuários prontos: os
 * fluxos aqui testados são justamente os que criam essas entidades, e um cenário pré-montado
 * mascararia o comportamento sob teste (BR-207).
 */
abstract class AuthTestSupport extends IntegrationTestSupport {

    protected static final String VALID_PASSWORD = "SenhaForte123";

    @Autowired protected AuthService authService;
    @Autowired protected PasswordResetService passwordResetService;
    @Autowired protected RefreshTokenService refreshTokenService;
    @Autowired protected VerificationTokenService verificationTokenService;
    @Autowired protected VerificationTokenRepository verificationTokenRepository;
    @Autowired protected RefreshTokenRepository refreshTokenRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected TenantRepository tenantRepository;
    @Autowired protected MembershipRepository membershipRepository;
    @Autowired protected TransactionTemplate transactionTemplate;
    @Autowired protected com.devtime.shared.tenancy.TenantContext tenantContext;
    @Autowired protected javax.sql.DataSource dataSource;

    @BeforeEach
    void resetContext() {
        SecurityContextHolder.clearContext();
        tenantContext.clear();
    }

    /**
     * Executa a ação no contexto do tenant indicado, como faria uma requisição autenticada.
     *
     * <p>Necessário para toda escrita em entidade tenant-scoped: o {@code AuditListener} preenche
     * {@code tenant_id} a partir do contexto e lança quando ele está vazio (BR-043) — é a mesma
     * proteção que impede um job de gravar sem tenant.
     */
    protected <T> T runInTenant(
            UUID tenantId, UUID userId, com.devtime.shared.security.Role role, Supplier<T> action) {
        tenantContext.set(
                new com.devtime.shared.tenancy.TenantSession(
                        userId,
                        tenantId,
                        null,
                        role,
                        com.devtime.shared.security.RolePermissions.of(role),
                        "America/Sao_Paulo"));
        try {
            return transactionTemplate.execute(status -> action.get());
        } finally {
            tenantContext.clear();
        }
    }

    /**
     * Popula o {@code SecurityContext} como o {@code JwtAuthenticationFilter} faria.
     *
     * <p>Necessário para chamar serviços com {@code @PreAuthorize}: a expressão é avaliada sobre a
     * {@code Authentication} do contexto, e o teste que invoca o serviço diretamente não passa pela
     * cadeia de filtros que a preencheria.
     */
    protected void authenticateAs(UUID userId) {
        var claims =
                new com.devtime.shared.security.AccessTokenClaims(
                        userId,
                        null,
                        null,
                        null,
                        "America/Sao_Paulo",
                        UUID.randomUUID(),
                        com.devtime.support.FixedClockTestConfiguration.FIXED_INSTANT,
                        com.devtime.support.FixedClockTestConfiguration.FIXED_INSTANT.plusSeconds(
                                900));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new org.springframework.security.authentication
                                .UsernamePasswordAuthenticationToken(
                                claims, null, java.util.List.of()));
    }

    /** Metadados de requisição estáveis, para que o teste não dependa de ambiente. */
    protected AuthService.RequestMetadata metadata() {
        return new AuthService.RequestMetadata("JUnit/1.0", "203.0.113.10");
    }

    /**
     * Cadastro com e-mail único por execução.
     *
     * <p>O sufixo aleatório existe porque {@code users.email} é único global e as classes de teste
     * compartilham o mesmo banco por container.
     */
    protected RegisterRequest registerRequest(String prefix) {
        return new RegisterRequest(
                prefix + "-" + UUID.randomUUID() + "@exemplo.com",
                VALID_PASSWORD,
                "Rafael Mendes",
                "Rafael Mendes Dev",
                "America/Sao_Paulo",
                true);
    }
}
