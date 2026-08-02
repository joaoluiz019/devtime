package com.devtime.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato do {@link TenantContext}.
 *
 * <p>TI-06 / BR-043 é o teste mais importante desta classe: contexto vazio deve <b>lançar</b>. Se
 * em algum momento passar a retornar {@code null} ou um valor padrão, toda consulta tenant-scoped
 * perderia o filtro e retornaria dados de todos os tenants (CE-A-07 de architecture.md).
 */
class TenantContextTest {

    private final TenantContext context = new TenantContext();

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @AfterEach
    void clear() {
        context.clear();
    }

    @Test
    @DisplayName("TI-06: requireTenantId lança exceção quando não há sessão")
    void requireTenantIdMustThrowWithoutSession() {
        assertThatThrownBy(context::requireTenantId)
                .as("nunca degradar para 'todos os tenants'")
                .isInstanceOf(TenantContextNotInitializedException.class);
    }

    @Test
    @DisplayName(
            "TI-06: requireTenantId lança exceção no token de pré-seleção, sem tenant escolhido")
    void requireTenantIdMustThrowWithoutSelectedTenant() {
        context.set(new TenantSession(userId, null, null, null, Set.of(), null));

        assertThatThrownBy(context::requireTenantId)
                .isInstanceOf(TenantContextNotInitializedException.class);
    }

    @Test
    @DisplayName("requireUserId lança exceção quando não há sessão")
    void requireUserIdMustThrowWithoutSession() {
        assertThatThrownBy(context::requireUserId)
                .isInstanceOf(TenantContextNotInitializedException.class);
    }

    @Test
    @DisplayName("O contexto expõe tenant, usuário, papel e permissões da sessão")
    void shouldExposeSessionData() {
        context.set(
                new TenantSession(
                        userId,
                        tenantId,
                        UUID.randomUUID(),
                        Role.MANAGER,
                        Set.of(Permission.CLIENT_VIEW),
                        "America/Sao_Paulo"));

        assertThat(context.isAuthenticated()).isTrue();
        assertThat(context.requireTenantId()).isEqualTo(tenantId);
        assertThat(context.requireUserId()).isEqualTo(userId);
        assertThat(context.currentRole()).contains(Role.MANAGER);
        assertThat(context.currentPermissions()).containsExactly(Permission.CLIENT_VIEW);
        assertThat(context.currentTimezone()).contains("America/Sao_Paulo");
    }

    @Test
    @DisplayName("clear remove a sessão: thread reutilizada não herda o tenant anterior")
    void clearMustRemoveSession() {
        context.set(
                new TenantSession(
                        userId, tenantId, null, Role.OWNER, Set.of(), "America/Sao_Paulo"));

        context.clear();

        assertThat(context.isAuthenticated()).isFalse();
        assertThat(context.currentTenantId()).isEmpty();
        assertThat(context.currentPermissions()).isEmpty();
    }

    @Test
    @DisplayName("BR-049: a sessão de sistema não possui usuário, e requireUserId falha alto")
    void systemSessionHasNoUser() {
        context.set(TenantSession.system(tenantId, Role.OWNER, Set.of()));

        // CE-S-06: é o que faz a trilha registrar actorType = SYSTEM.
        assertThat(context.currentUserId()).isEmpty();
        assertThat(context.requireTenantId()).isEqualTo(tenantId);
        // CG-06: quem precisa de usuário não recebe null silenciosamente.
        assertThatThrownBy(context::requireUserId)
                .isInstanceOf(TenantContextNotInitializedException.class);
    }

    @Test
    @DisplayName("TenantSession com permissões nulas expõe conjunto vazio, nunca null")
    void sessionMustNormalizeNullPermissions() {
        var session = new TenantSession(userId, tenantId, null, Role.OWNER, null, null);

        assertThat(session.permissions()).isEmpty();
        assertThat(session.hasTenantSelected()).isTrue();
    }
}
