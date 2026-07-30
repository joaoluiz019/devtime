package com.devtime.shared.security;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Matriz Papel × Permissão de permissions.md §7.
 *
 * <p>Na matriz original, ✅ e 🔸 significam ambos <b>permissão concedida</b>; 🔸 apenas sinaliza que
 * uma restrição adicional de ownership (§8) ou de escopo de dados (§9) se aplica <i>depois</i> da
 * verificação de permissão. Essas restrições não pertencem a este mapa: são verificadas no serviço,
 * após a permissão (AZ-03), e o escopo é aplicado na consulta (IMP-02, AZ-04).
 *
 * <p>{@link Role#CLIENT_PORTAL} não consta na matriz — é reservado para v2.x. Seu conjunto é
 * deliberadamente vazio: ART-085 nega por padrão, então um papel sem linha na matriz não recebe
 * nenhuma permissão em vez de receber um palpite.
 */
public final class RolePermissions {

    private static final Set<Permission> ALL = EnumSet.allOf(Permission.class);

    private static final Map<Role, Set<Permission>> BY_ROLE = buildMatrix();

    private RolePermissions() {}

    /** Permissões do papel. Nunca retorna {@code null} (ER-06). */
    public static Set<Permission> of(Role role) {
        if (role == null) {
            return Set.of();
        }
        return BY_ROLE.getOrDefault(role, Set.of());
    }

    public static boolean grants(Role role, Permission permission) {
        return of(role).contains(permission);
    }

    private static Map<Role, Set<Permission>> buildMatrix() {
        Map<Role, Set<Permission>> matrix = new EnumMap<>(Role.class);

        // OWNER: controle total.
        matrix.put(Role.OWNER, Set.copyOf(ALL));

        // ADMIN: tudo que o OWNER faz, exceto excluir o tenant e gerenciar faturamento (§5).
        // A restrição de não agir sobre um OWNER (nota ¹) é de ownership, verificada no serviço.
        matrix.put(Role.ADMIN, complementOf(Permission.TENANT_DELETE, Permission.TENANT_BILLING));

        // MANAGER: gestão de entrega. Não gerencia membros, configurações do tenant nem períodos.
        matrix.put(
                Role.MANAGER,
                setOf(
                        Permission.TENANT_VIEW,
                        Permission.MEMBER_VIEW,
                        Permission.CLIENT_VIEW,
                        Permission.CLIENT_CREATE,
                        Permission.CLIENT_UPDATE,
                        Permission.CONTRACT_VIEW,
                        Permission.CONTRACT_CREATE,
                        Permission.CONTRACT_UPDATE,
                        Permission.CONTRACT_TRANSITION,
                        Permission.CONTRACT_VIEW_FINANCIAL,
                        Permission.PERIOD_VIEW,
                        Permission.TICKET_VIEW,
                        Permission.TICKET_CREATE,
                        Permission.TICKET_UPDATE_OWN,
                        Permission.TICKET_UPDATE_ANY,
                        Permission.TICKET_ASSIGN,
                        Permission.TICKET_TRANSITION,
                        Permission.TICKET_DELETE,
                        Permission.WORKLOG_VIEW_OWN,
                        Permission.WORKLOG_VIEW_ANY,
                        Permission.WORKLOG_CREATE,
                        Permission.WORKLOG_CREATE_FOR_OTHER,
                        Permission.WORKLOG_UPDATE_OWN,
                        Permission.WORKLOG_UPDATE_ANY,
                        Permission.WORKLOG_DELETE_OWN,
                        Permission.WORKLOG_DELETE_ANY,
                        Permission.TIMER_USE,
                        Permission.TIMER_VIEW_ANY,
                        Permission.CATEGORY_VIEW,
                        Permission.CATEGORY_MANAGE,
                        Permission.TAG_VIEW,
                        Permission.TAG_MANAGE,
                        Permission.COMMENT_VIEW,
                        Permission.COMMENT_CREATE,
                        Permission.COMMENT_UPDATE_OWN,
                        Permission.COMMENT_DELETE_ANY,
                        Permission.ATTACHMENT_VIEW,
                        Permission.ATTACHMENT_UPLOAD,
                        Permission.ATTACHMENT_DELETE_OWN,
                        Permission.ATTACHMENT_DELETE_ANY,
                        Permission.REPORT_VIEW_OWN,
                        Permission.REPORT_VIEW_ANY,
                        Permission.REPORT_EXPORT,
                        Permission.DASHBOARD_VIEW_OWN,
                        Permission.DASHBOARD_VIEW_ANY,
                        Permission.NOTIFICATION_VIEW));

        // MEMBER: executor. Registra as próprias horas; não vê horas de colegas (§9).
        matrix.put(
                Role.MEMBER,
                setOf(
                        Permission.TENANT_VIEW,
                        Permission.MEMBER_VIEW,
                        Permission.CLIENT_VIEW,
                        Permission.CONTRACT_VIEW,
                        Permission.PERIOD_VIEW,
                        Permission.TICKET_VIEW,
                        Permission.TICKET_CREATE,
                        Permission.TICKET_UPDATE_OWN,
                        Permission.TICKET_ASSIGN,
                        Permission.TICKET_TRANSITION,
                        Permission.WORKLOG_VIEW_OWN,
                        Permission.WORKLOG_CREATE,
                        Permission.WORKLOG_UPDATE_OWN,
                        Permission.WORKLOG_DELETE_OWN,
                        Permission.TIMER_USE,
                        Permission.CATEGORY_VIEW,
                        Permission.TAG_VIEW,
                        Permission.TAG_MANAGE,
                        Permission.COMMENT_VIEW,
                        Permission.COMMENT_CREATE,
                        Permission.COMMENT_UPDATE_OWN,
                        Permission.ATTACHMENT_VIEW,
                        Permission.ATTACHMENT_UPLOAD,
                        Permission.ATTACHMENT_DELETE_OWN,
                        Permission.REPORT_VIEW_OWN,
                        Permission.REPORT_EXPORT,
                        Permission.DASHBOARD_VIEW_OWN,
                        Permission.NOTIFICATION_VIEW));

        // VIEWER: somente leitura, com visão financeira consolidada (§9). Não escreve nada.
        matrix.put(
                Role.VIEWER,
                setOf(
                        Permission.TENANT_VIEW,
                        Permission.MEMBER_VIEW,
                        Permission.CLIENT_VIEW,
                        Permission.CONTRACT_VIEW,
                        Permission.CONTRACT_VIEW_FINANCIAL,
                        Permission.PERIOD_VIEW,
                        Permission.TICKET_VIEW,
                        Permission.WORKLOG_VIEW_OWN,
                        Permission.WORKLOG_VIEW_ANY,
                        Permission.CATEGORY_VIEW,
                        Permission.TAG_VIEW,
                        Permission.COMMENT_VIEW,
                        Permission.ATTACHMENT_VIEW,
                        Permission.REPORT_VIEW_OWN,
                        Permission.REPORT_VIEW_ANY,
                        Permission.REPORT_EXPORT,
                        Permission.DASHBOARD_VIEW_OWN,
                        Permission.DASHBOARD_VIEW_ANY,
                        Permission.NOTIFICATION_VIEW));

        matrix.put(Role.CLIENT_PORTAL, Set.of());

        return Map.copyOf(matrix);
    }

    private static Set<Permission> setOf(Permission... permissions) {
        return Set.copyOf(EnumSet.copyOf(java.util.Arrays.asList(permissions)));
    }

    private static Set<Permission> complementOf(Permission... denied) {
        EnumSet<Permission> granted = EnumSet.allOf(Permission.class);
        granted.removeAll(EnumSet.copyOf(java.util.Arrays.asList(denied)));
        return Set.copyOf(granted);
    }
}
