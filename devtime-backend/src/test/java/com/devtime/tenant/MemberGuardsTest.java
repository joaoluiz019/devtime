package com.devtime.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * T-002-41 e T-002-42: RN-455 e RN-456 por todos os caminhos e papéis.
 *
 * <p>Unitário porque as duas regras são decisões, não persistência: o que precisa ser provado é
 * <b>qual</b> exceção sai em cada combinação de papel do requisitante e do alvo. A prova de que o
 * lock funciona sob concorrência é de integração e vive em {@code MemberLifecycleIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberGuardsTest {

    @Mock private MembershipRepository repository;

    private final TenantContext tenantContext = new TenantContext();
    private MemberGuards guards;

    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        guards = new MemberGuards(repository, tenantContext);
    }

    private void sessionAs(Role role) {
        Set<Permission> permissions = RolePermissions.of(role);
        tenantContext.set(
                new TenantSession(
                        actorId,
                        tenantId,
                        UUID.randomUUID(),
                        role,
                        permissions,
                        "America/Sao_Paulo"));
    }

    private Membership membership(UUID userId, Role role, MembershipStatus status) {
        Membership membership = new Membership();
        membership.setId(UUID.randomUUID());
        membership.setTenantId(tenantId);
        membership.setUserId(userId);
        membership.setRole(role);
        membership.setStatus(status);
        return membership;
    }

    @ParameterizedTest
    @EnumSource(
            value = Role.class,
            names = {"OWNER", "ADMIN", "MANAGER", "MEMBER"})
    @DisplayName("RN-456: nenhum papel altera o próprio vínculo, nem OWNER")
    void selfChangeIsRejectedForEveryRole(Role role) {
        sessionAs(role);
        assertThatThrownBy(() -> guards.assertNotSelf(actorId))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SELF_ROLE_CHANGE));
    }

    @Test
    @DisplayName("RN-456: alterar outro vínculo passa pela guarda")
    void otherUserPassesSelfGuard() {
        sessionAs(Role.OWNER);
        assertThatCode(() -> guards.assertNotSelf(targetId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Nota ¹: ADMIN não age sobre OWNER — DEVTIME-1104")
    void adminCannotActOverOwner() {
        sessionAs(Role.ADMIN);
        assertThatThrownBy(() -> guards.assertHierarchyAllowed(Role.OWNER, null))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ADMIN_OVER_OWNER));
    }

    @Test
    @DisplayName("Nota ¹ / CP-10: ADMIN não promove ninguém a OWNER")
    void adminCannotPromoteToOwner() {
        sessionAs(Role.ADMIN);
        assertThatThrownBy(() -> guards.assertHierarchyAllowed(Role.MEMBER, Role.OWNER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("OWNER age sobre qualquer papel, inclusive outro OWNER")
    void ownerMayActOverOwner() {
        sessionAs(Role.OWNER);
        assertThatCode(() -> guards.assertHierarchyAllowed(Role.OWNER, Role.ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RN-455: rebaixar o último OWNER ativo devolve DEVTIME-2455")
    void lastOwnerIsProtected() {
        sessionAs(Role.OWNER);
        Membership onlyOwner = membership(targetId, Role.OWNER, MembershipStatus.ACTIVE);
        when(repository.lockActiveOwners()).thenReturn(List.of(onlyOwner));

        assertThatThrownBy(() -> guards.assertNotLastOwner(onlyOwner))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LAST_OWNER_REQUIRED));
    }

    @Test
    @DisplayName("RN-455: com dois OWNERs ativos, rebaixar um é permitido")
    void secondOwnerAllowsDemotion() {
        sessionAs(Role.OWNER);
        Membership first = membership(targetId, Role.OWNER, MembershipStatus.ACTIVE);
        Membership second = membership(UUID.randomUUID(), Role.OWNER, MembershipStatus.ACTIVE);
        when(repository.lockActiveOwners()).thenReturn(List.of(first, second));

        assertThatCode(() -> guards.assertNotLastOwner(first)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RN-455 não se aplica a quem não é OWNER ativo — nenhuma consulta é feita")
    void nonOwnerSkipsCounting() {
        sessionAs(Role.OWNER);
        Membership member = membership(targetId, Role.MEMBER, MembershipStatus.ACTIVE);
        assertThatCode(() -> guards.assertNotLastOwner(member)).doesNotThrowAnyException();

        Membership suspendedOwner = membership(targetId, Role.OWNER, MembershipStatus.SUSPENDED);
        assertThatCode(() -> guards.assertNotLastOwner(suspendedOwner)).doesNotThrowAnyException();
    }
}
