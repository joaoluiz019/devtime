package com.devtime.tenant;

import com.devtime.shared.security.Role;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.dto.MemberResponses.MemberResponse;
import com.devtime.tenant.dto.MemberResponses.MemberUserResponse;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import com.devtime.user.dto.UserAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Conversão de {@link Membership} + conta para a resposta de users.md §7.1.
 *
 * <p>Achata dois agregados e calcula {@code availableActions} (ME-06). O cálculo é apresentação, e
 * não regra: as guardas de {@link MemberGuards} continuam sendo aplicadas no serviço, e esta lista
 * apenas antecipa o resultado para que a UI não ofereça um botão que resultaria em {@code
 * DEVTIME-1104} ou {@code DEVTIME-2456}.
 *
 * <p>BR-105: não acessa banco — as contas chegam resolvidas em lote pelo serviço.
 */
@Component
@RequiredArgsConstructor
public class MemberMapper {

    static final String ACTION_CHANGE_ROLE = "CHANGE_ROLE";
    static final String ACTION_SUSPEND = "SUSPEND";
    static final String ACTION_REACTIVATE = "REACTIVATE";
    static final String ACTION_REMOVE = "REMOVE";
    static final String ACTION_RESEND_INVITATION = "RESEND_INVITATION";

    private final TenantContext tenantContext;

    public MemberResponse toResponse(Membership membership, Map<UUID, UserAccount> accounts) {
        UserAccount account = accounts.get(membership.getUserId());
        return new MemberResponse(
                membership.getId(),
                toUser(membership.getUserId(), account),
                membership.getRole(),
                MembershipState.valueOf(membership.getStatus().name()),
                membership.getInvitedAt(),
                membership.getAcceptedAt(),
                availableActions(membership),
                membership.getVersion() == null ? 0L : membership.getVersion());
    }

    /**
     * RN-458: o vínculo sobrevive à conta.
     *
     * <p>Uma conta ausente devolve o identificador com nome substituto em vez de nulo — a linha
     * precisa continuar existindo para que a organização veja quem já teve acesso.
     */
    private MemberUserResponse toUser(UUID userId, UserAccount account) {
        if (account == null) {
            return new MemberUserResponse(
                    userId, com.devtime.user.dto.UserSummary.REMOVED_USER_NAME, null, null, null);
        }
        return new MemberUserResponse(
                account.id(),
                account.fullName(),
                account.displayName(),
                account.email(),
                account.avatarUrl());
    }

    private List<String> availableActions(Membership membership) {
        UUID actorId = tenantContext.currentUserId().orElse(null);
        Role actorRole = tenantContext.currentRole().orElse(null);
        var permissions = tenantContext.currentPermissions();

        boolean isSelf = membership.getUserId().equals(actorId);
        boolean targetIsOwner = membership.getRole() == Role.OWNER;
        // Nota ¹: ADMIN não altera, suspende nem remove um OWNER.
        boolean hierarchyBlocked = targetIsOwner && actorRole != Role.OWNER;

        List<String> actions = new ArrayList<>();
        if (membership.getStatus() == MembershipStatus.INVITED) {
            if (permissions.contains(com.devtime.shared.security.Permission.MEMBER_INVITE)) {
                actions.add(ACTION_RESEND_INVITATION);
            }
            if (permissions.contains(com.devtime.shared.security.Permission.MEMBER_REMOVE)) {
                actions.add(ACTION_REMOVE);
            }
            return List.copyOf(actions);
        }
        if (hierarchyBlocked) {
            return List.of();
        }
        // RN-456: nenhuma ação sobre o próprio papel, nem sendo OWNER.
        if (!isSelf
                && permissions.contains(
                        com.devtime.shared.security.Permission.MEMBER_UPDATE_ROLE)) {
            actions.add(ACTION_CHANGE_ROLE);
        }
        if (!isSelf
                && permissions.contains(com.devtime.shared.security.Permission.MEMBER_SUSPEND)) {
            actions.add(
                    membership.getStatus() == MembershipStatus.SUSPENDED
                            ? ACTION_REACTIVATE
                            : ACTION_SUSPEND);
        }
        if (!isSelf && permissions.contains(com.devtime.shared.security.Permission.MEMBER_REMOVE)) {
            actions.add(ACTION_REMOVE);
        }
        return List.copyOf(actions);
    }
}
