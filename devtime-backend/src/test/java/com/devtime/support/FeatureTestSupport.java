package com.devtime.support;

import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.tenant.MembershipRepository;
import com.devtime.tenant.TenantRepository;
import com.devtime.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base dos testes de integração das features de negócio.
 *
 * <p>Cria dois tenants completos e oferece {@link #runAs} para executar operações no contexto de um
 * deles. Ter <b>dois</b> tenants no cenário padrão, e não um, é deliberado: qualquer teste de
 * feature passa a poder verificar isolamento sem montar cenário extra, o que é o que torna BR-208
 * ("todo endpoint possui classe de teste de isolamento") praticável.
 */
public abstract class FeatureTestSupport extends IntegrationTestSupport {

    protected static final Instant NOW = FixedClockTestConfiguration.FIXED_INSTANT;

    @Autowired protected TenantRepository tenantRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected MembershipRepository membershipRepository;
    @Autowired protected TenantContext tenantContext;
    @Autowired protected TransactionTemplate transactionTemplate;
    @Autowired protected MutableTestClock clock;

    protected UUID tenantAId;
    protected UUID tenantBId;
    protected UUID userAId;
    protected UUID userBId;

    @BeforeEach
    void createTenants() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenantContext.clear();
        // O contexto Spring é compartilhado: sem o reset, um avanço de relógio feito por um teste
        // de cronômetro faria outro teste falhar conforme a ordem de execução.
        clock.reset();

        tenantAId =
                inTransaction(
                        () ->
                                tenantRepository
                                        .save(FoundationDataBuilder.tenant("a-" + suffix))
                                        .getId());
        tenantBId =
                inTransaction(
                        () ->
                                tenantRepository
                                        .save(FoundationDataBuilder.tenant("b-" + suffix))
                                        .getId());
        userAId =
                inTransaction(
                        () ->
                                userRepository
                                        .save(
                                                FoundationDataBuilder.user(
                                                        "a-" + suffix + "@exemplo.com", NOW))
                                        .getId());
        userBId =
                inTransaction(
                        () ->
                                userRepository
                                        .save(
                                                FoundationDataBuilder.user(
                                                        "b-" + suffix + "@exemplo.com", NOW))
                                        .getId());

        runAs(
                tenantAId,
                userAId,
                Role.OWNER,
                () ->
                        membershipRepository.save(
                                FoundationDataBuilder.membership(
                                        tenantAId, userAId, Role.OWNER, NOW)));
        runAs(
                tenantBId,
                userBId,
                Role.OWNER,
                () ->
                        membershipRepository.save(
                                FoundationDataBuilder.membership(
                                        tenantBId, userBId, Role.OWNER, NOW)));
    }

    @AfterEach
    void clearTenantContext() {
        tenantContext.clear();
    }

    /** Executa como {@code OWNER} do tenant A — o caso mais comum nos testes de regra. */
    protected <T> T asOwnerOfA(Supplier<T> operation) {
        return runAs(tenantAId, userAId, Role.OWNER, operation);
    }

    protected <T> T asOwnerOfB(Supplier<T> operation) {
        return runAs(tenantBId, userBId, Role.OWNER, operation);
    }

    /**
     * Executa uma operação transacional no contexto do tenant e papel informados.
     *
     * <p>As permissões vêm de {@link RolePermissions}, e não de um conjunto fixo: um teste que
     * concedesse permissões manualmente deixaria de detectar divergência com a matriz.
     */
    protected <T> T runAs(UUID tenantId, UUID userId, Role role, Supplier<T> operation) {
        // O contexto anterior é preservado e restaurado: chamadas aninhadas de runAs são comuns nos
        // testes (criar o cliente dentro do cenário do contrato), e limpar ao sair da interna
        // deixaria a externa sem sessão — falha que se manifestaria como erro de autenticação, não
        // como o comportamento sob teste.
        var previousSession = tenantContext.session().orElse(null);
        var previousAuthentication = SecurityContextHolder.getContext().getAuthentication();

        Set<Permission> permissions = RolePermissions.of(role);
        tenantContext.set(
                new TenantSession(
                        userId,
                        tenantId,
                        UUID.randomUUID(),
                        role,
                        permissions,
                        "America/Sao_Paulo"));
        // O SecurityContext é populado pelo JwtAuthenticationFilter em produção; sem ele,
        // @PreAuthorize falha por ausência de Authentication antes de avaliar a permissão. As
        // authorities ficam vazias de propósito: AZ-02 exige que a permissão venha do papel no
        // TenantContext, nunca de authorities do token — um teste que as preenchesse deixaria de
        // provar isso.
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        try {
            return transactionTemplate.execute(status -> operation.get());
        } finally {
            if (previousSession == null) {
                tenantContext.clear();
            } else {
                tenantContext.set(previousSession);
            }
            SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }

    protected <T> T inTransaction(Supplier<T> operation) {
        return transactionTemplate.execute(status -> operation.get());
    }
}
