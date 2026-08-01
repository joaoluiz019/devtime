package com.devtime.worklog;

import com.devtime.shared.error.OwnershipViolationException;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.tenant.MembershipService;
import com.devtime.worklog.domain.WorkLog;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Propriedade e escopo do registro de horas (RN-106, RN-122, OWN-01, OWN-02).
 *
 * <p><b>OWN-01: o registro pertence a quem trabalhou, não a quem o lançou.</b> Um {@code MANAGER}
 * que lança em nome de outro membro (RN-106) não se torna dono — o membro é. A consequência prática
 * importa: o membro pode editar um registro criado por terceiro em seu nome.
 *
 * <p><b>OWN-02: ownership não sobrepõe guarda de estado.</b> Ser dono não permite editar um
 * registro travado por período fechado; isso é de {@link LockedPeriodGuard} e é verificado
 * separadamente.
 */
@Component
@RequiredArgsConstructor
public class WorkLogOwnershipPolicy {

    private final TenantContext tenantContext;
    private final MembershipService membershipService;

    /**
     * RN-106: resolve o dono do registro a ser criado.
     *
     * <p>O padrão é o usuário autenticado. Informar outro exige {@code WORKLOG_CREATE_FOR_OTHER} —
     * registrar em nome de terceiro é responsabilidade hierárquica — e que o destinatário seja
     * membro <b>ativo</b> do tenant (CX-25): lançar horas para um membro suspenso criaria um
     * registro que ninguém pode editar nem justificar.
     *
     * @param requestedUserId {@code userId} do payload; nulo significa "para mim"
     * @throws OwnershipViolationException quando falta permissão ou o membro não está ativo
     */
    public UUID resolveOwner(UUID requestedUserId) {
        UUID currentUserId = tenantContext.requireUserId();
        if (requestedUserId == null || requestedUserId.equals(currentUserId)) {
            return currentUserId;
        }
        if (!hasPermission(Permission.WORKLOG_CREATE_FOR_OTHER)) {
            throw new OwnershipViolationException("WorkLog");
        }
        if (!membershipService.isActiveMember(requestedUserId)) {
            throw new OwnershipViolationException("WorkLog");
        }
        return requestedUserId;
    }

    /**
     * RN-122: o autor edita o próprio; {@code MANAGER}+ edita o de qualquer membro.
     *
     * <p>{@code VIEWER} não possui nenhuma das duas permissões e nunca chega aqui — é barrado no
     * {@code @PreAuthorize} do serviço.
     */
    public void assertCanUpdate(WorkLog workLog) {
        assertOwnOr(workLog, Permission.WORKLOG_UPDATE_ANY);
    }

    /** RN-122 aplicada à exclusão. */
    public void assertCanDelete(WorkLog workLog) {
        assertOwnOr(workLog, Permission.WORKLOG_DELETE_ANY);
    }

    /**
     * §9 de permissions.md: {@code MEMBER} enxerga apenas os próprios registros.
     *
     * <p>É restrição de <b>privacidade</b>, não apenas de negócio: o conjunto de work logs de uma
     * pessoa revela seu padrão de trabalho — horários, jornada, dias produtivos (§19.1).
     *
     * @return o {@code userId} ao qual a consulta deve ser restrita, ou vazio quando o requisitante
     *     enxerga todo o tenant
     */
    public java.util.Optional<UUID> dataScopeUserId() {
        if (hasPermission(Permission.WORKLOG_VIEW_ANY)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(tenantContext.requireUserId());
    }

    /**
     * CE-P-04: registro de colega acessado por id direto responde {@code 404}, nunca {@code 403}.
     *
     * <p>Distinguir os dois permitiria a um {@code MEMBER} descobrir, por tentativa, quais
     * registros existem — e a existência já é informação sobre quem trabalhou em quê.
     */
    public boolean isVisible(WorkLog workLog) {
        return dataScopeUserId().map(workLog.getUserId()::equals).orElse(true);
    }

    private void assertOwnOr(WorkLog workLog, Permission anyPermission) {
        if (hasPermission(anyPermission)) {
            return;
        }
        if (!tenantContext.requireUserId().equals(workLog.getUserId())) {
            throw new OwnershipViolationException("WorkLog");
        }
    }

    private boolean hasPermission(Permission permission) {
        return tenantContext.currentPermissions().contains(permission);
    }
}
