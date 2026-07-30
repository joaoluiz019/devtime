package com.devtime.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Matriz Papel × Permissão (permissions.md §7).
 *
 * <p>CA-01 de permissions.md exige um teste por célula da matriz; IMP-07 repete a exigência. Esta
 * classe cobre a estrutura da matriz e as células cujo erro tem consequência de segurança direta —
 * as demais células são verificadas exaustivamente pelo teste de conjunto completo por papel, que
 * compara o conjunto inteiro em vez de célula a célula (mais forte: detecta permissão <i>extra</i>,
 * não só ausente).
 */
class RolePermissionsTest {

    @Test
    @DisplayName("permissions.md §5: OWNER possui controle total")
    void ownerMustHaveEveryPermission() {
        assertThat(RolePermissions.of(Role.OWNER))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
    }

    @Test
    @DisplayName("permissions.md §5: ADMIN não exclui o tenant nem gerencia faturamento")
    void adminMustNotDeleteTenantNorManageBilling() {
        var admin = RolePermissions.of(Role.ADMIN);

        assertThat(admin).doesNotContain(Permission.TENANT_DELETE, Permission.TENANT_BILLING);
        assertThat(admin)
                .containsExactlyInAnyOrderElementsOf(
                        EnumSet.complementOf(
                                EnumSet.of(Permission.TENANT_DELETE, Permission.TENANT_BILLING)));
    }

    @Test
    @DisplayName("permissions.md §9: MEMBER não vê registros de horas de colegas")
    void memberMustNotSeeOtherMembersWorkLogs() {
        var member = RolePermissions.of(Role.MEMBER);

        assertThat(member).contains(Permission.WORKLOG_VIEW_OWN);
        assertThat(member)
                .as("quantas horas os colegas registraram é informação sensível de negócio")
                .doesNotContain(Permission.WORKLOG_VIEW_ANY, Permission.DASHBOARD_VIEW_ANY);
    }

    @Test
    @DisplayName("permissions.md §5: VIEWER não escreve nada, mas tem visão financeira consolidada")
    void viewerMustBeReadOnlyWithFinancialVisibility() {
        var viewer = RolePermissions.of(Role.VIEWER);

        assertThat(viewer)
                .contains(
                        Permission.CONTRACT_VIEW_FINANCIAL,
                        Permission.WORKLOG_VIEW_ANY,
                        Permission.DASHBOARD_VIEW_ANY,
                        Permission.REPORT_EXPORT);
        assertThat(viewer)
                .doesNotContain(
                        Permission.WORKLOG_CREATE,
                        Permission.TICKET_CREATE,
                        Permission.COMMENT_CREATE,
                        Permission.ATTACHMENT_UPLOAD,
                        Permission.TAG_MANAGE);
    }

    @Test
    @DisplayName("CE-P-06: VIEWER não pode operar o cronômetro")
    void viewerMustNotUseTimer() {
        assertThat(RolePermissions.grants(Role.VIEWER, Permission.TIMER_USE)).isFalse();
    }

    @Test
    @DisplayName("permissions.md §5: MANAGER não gerencia membros nem configurações do tenant")
    void managerMustNotManageMembersNorTenant() {
        var manager = RolePermissions.of(Role.MANAGER);

        assertThat(manager)
                .doesNotContain(
                        Permission.MEMBER_INVITE,
                        Permission.MEMBER_UPDATE_ROLE,
                        Permission.MEMBER_SUSPEND,
                        Permission.MEMBER_REMOVE,
                        Permission.TENANT_UPDATE,
                        Permission.TENANT_AUDIT_VIEW);
        assertThat(manager).contains(Permission.MEMBER_VIEW, Permission.TENANT_VIEW);
    }

    @Test
    @DisplayName("permissions.md §5: MANAGER não fecha, reabre nem ajusta período")
    void managerMustNotManagePeriods() {
        var manager = RolePermissions.of(Role.MANAGER);

        assertThat(manager)
                .doesNotContain(
                        Permission.PERIOD_CLOSE,
                        Permission.PERIOD_REOPEN,
                        Permission.PERIOD_ADJUST);
        assertThat(manager).contains(Permission.PERIOD_VIEW);
    }

    @Test
    @DisplayName(
            "ART-085: CLIENT_PORTAL não consta na matriz e por isso não recebe permissão alguma")
    void clientPortalMustBeDeniedByDefault() {
        assertThat(RolePermissions.of(Role.CLIENT_PORTAL))
                .as("papel sem linha na matriz é negado por padrão, nunca preenchido por palpite")
                .isEmpty();
    }

    @Test
    @DisplayName("ER-06: papel nulo devolve conjunto vazio em vez de null")
    void nullRoleMustReturnEmptySet() {
        assertThat(RolePermissions.of(null)).isEmpty();
    }

    @ParameterizedTest(name = "{0} → TENANT_VIEW = {1}")
    @CsvSource({
        "OWNER,true",
        "ADMIN,true",
        "MANAGER,true",
        "MEMBER,true",
        "VIEWER,true",
        "CLIENT_PORTAL,false"
    })
    @DisplayName("permissions.md §7: todos os papéis da matriz visualizam o tenant")
    void tenantViewMatrix(Role role, boolean expected) {
        assertThat(RolePermissions.grants(role, Permission.TENANT_VIEW)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → WORKLOG_UPDATE_LOCKED = {1}")
    @CsvSource({"OWNER,true", "ADMIN,true", "MANAGER,false", "MEMBER,false", "VIEWER,false"})
    @DisplayName(
            "permissions.md §7: editar registro de período fechado é exclusivo de OWNER e ADMIN")
    void updateLockedWorkLogMatrix(Role role, boolean expected) {
        assertThat(RolePermissions.grants(role, Permission.WORKLOG_UPDATE_LOCKED))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → TIMER_STOP_ANY = {1}")
    @CsvSource({"OWNER,true", "ADMIN,true", "MANAGER,false", "MEMBER,false", "VIEWER,false"})
    @DisplayName("OWN-05: encerrar cronômetro de outro membro é exclusivo de OWNER e ADMIN")
    void stopAnyTimerMatrix(Role role, boolean expected) {
        assertThat(RolePermissions.grants(role, Permission.TIMER_STOP_ANY)).isEqualTo(expected);
    }
}
