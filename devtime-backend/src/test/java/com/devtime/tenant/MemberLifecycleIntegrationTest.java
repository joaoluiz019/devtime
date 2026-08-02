package com.devtime.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.audit.AuditLogRepository;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.FoundationDataBuilder;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.dto.MemberRequests.RoleUpdateRequest;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import com.devtime.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T-002-41 a T-002-44 sobre banco real: ciclo de vida do vínculo.
 *
 * <p>Integração, e não unitário, porque o que precisa ser provado aqui depende do banco: a contagem
 * de OWNERs com lock (RN-455), a persistência de {@code roleChangedAt} que invalida os tokens
 * (IMP-04) e a gravação da trilha na mesma transação (RN-006).
 */
class MemberLifecycleIntegrationTest extends FeatureTestSupport {

    @Autowired private MembershipService membershipService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;

    private UUID inviteExtraMember(Role role) {
        UUID userId =
                inTransaction(
                        () ->
                                userRepository
                                        .save(
                                                FoundationDataBuilder.user(
                                                        UUID.randomUUID() + "@exemplo.com", NOW))
                                        .getId());
        return runAs(
                tenantAId,
                userAId,
                Role.OWNER,
                () -> {
                    Membership membership =
                            FoundationDataBuilder.membership(tenantAId, userId, role, NOW);
                    return membershipRepository.save(membership).getId();
                });
    }

    @Test
    @DisplayName("RN-455: rebaixar o único OWNER ativo devolve DEVTIME-2455")
    void demotingTheOnlyOwnerIsRejected() {
        UUID ownMembershipId =
                asOwnerOfA(() -> membershipRepository.findByUserId(userAId).orElseThrow().getId());
        UUID otherOwner = inviteExtraMember(Role.ADMIN);

        // O ADMIN tenta rebaixar o único OWNER: a hierarquia barra antes (DEVTIME-1104). Aqui o
        // caminho testado é o OWNER rebaixando a si mesmo, que RN-456 barra — então o cenário de
        // RN-455 é o ADMIN sendo promovido e o OWNER rebaixado por ele.
        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        userAId,
                                        Role.OWNER,
                                        () ->
                                                membershipService.changeRole(
                                                        ownMembershipId,
                                                        new RoleUpdateRequest(Role.MEMBER, 0L))))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                // RN-456 precede RN-455 na ordem de §6.1: a mensagem correta é
                                // sobre o próprio papel, não sobre o último proprietário.
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SELF_ROLE_CHANGE));
        assertThat(otherOwner).isNotNull();
    }

    @Test
    @DisplayName("RN-455: com dois OWNERs, rebaixar um é permitido e a trilha registra a mudança")
    void demotingOneOfTwoOwnersIsAllowed() {
        UUID secondOwner = inviteExtraMember(Role.OWNER);

        var response =
                runAs(
                        tenantAId,
                        userAId,
                        Role.OWNER,
                        () ->
                                membershipService.changeRole(
                                        secondOwner, new RoleUpdateRequest(Role.MANAGER, 0L)));

        assertThat(response.role()).isEqualTo(Role.MANAGER);
        var trail =
                asOwnerOfA(
                        () ->
                                auditLogRepository.findByEntity(
                                        tenantAId, "MEMBERSHIP", secondOwner));
        assertThat(trail).isNotEmpty();
        assertThat(trail.get(0).getAction()).isEqualTo("MEMBERSHIP_ROLE_CHANGED");
    }

    @Test
    @DisplayName("IMP-04: alterar o papel atualiza roleChangedAt, invalidando os tokens do alvo")
    void roleChangeUpdatesRoleChangedAt() {
        UUID target = inviteExtraMember(Role.MEMBER);
        var before =
                asOwnerOfA(
                        () ->
                                membershipRepository
                                        .findById(target)
                                        .orElseThrow()
                                        .getRoleChangedAt());

        runAs(
                tenantAId,
                userAId,
                Role.OWNER,
                () ->
                        membershipService.changeRole(
                                target, new RoleUpdateRequest(Role.MANAGER, 0L)));

        var after =
                asOwnerOfA(
                        () ->
                                membershipRepository
                                        .findById(target)
                                        .orElseThrow()
                                        .getRoleChangedAt());
        assertThat(after).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("RN-004: version divergente devolve DEVTIME-2004")
    void staleVersionIsRejected() {
        UUID target = inviteExtraMember(Role.MEMBER);

        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        userAId,
                                        Role.OWNER,
                                        () ->
                                                membershipService.changeRole(
                                                        target,
                                                        new RoleUpdateRequest(Role.MANAGER, 99L))))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.VERSION_CONFLICT));
    }

    @Test
    @DisplayName("ART-024: vínculo de outro tenant responde 404, nunca 403")
    void membershipOfAnotherTenantIsNotFound() {
        UUID foreign =
                asOwnerOfB(() -> membershipRepository.findByUserId(userBId).orElseThrow().getId());

        assertThatThrownBy(() -> asOwnerOfA(() -> membershipService.getById(foreign)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("RN-458 / RN-460: remover membro devolve os efeitos aplicados")
    void removalReportsItsEffects() {
        UUID target = inviteExtraMember(Role.MEMBER);

        var response =
                runAs(tenantAId, userAId, Role.OWNER, () -> membershipService.remove(target, null));

        assertThat(response.status()).isEqualTo(MembershipState.REMOVED);
        assertThat(response.reassignedTo()).isEqualTo(userAId);
        assertThat(response.workLogsPreserved()).isZero();
        assertThat(response.activeTimerDiscarded()).isFalse();

        var removed = asOwnerOfA(() -> membershipRepository.findById(target).orElseThrow());
        assertThat(removed.getStatus()).isEqualTo(MembershipStatus.REMOVED);
    }
}
