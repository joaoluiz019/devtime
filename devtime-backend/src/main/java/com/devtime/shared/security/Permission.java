package com.devtime.shared.security;

/**
 * Catálogo de permissões atômicas (permissions.md §6).
 *
 * <p>Nomeadas como {@code RECURSO_ACAO}. Nenhuma verificação de acesso pode existir no código sem
 * constar aqui e na matriz de {@link RolePermissions}. Adicionar uma permissão exige atualizar a
 * matriz de permissions.md §7, este enum e os testes de todas as células afetadas.
 */
public enum Permission {

    // §6.1 Tenant e configurações
    TENANT_VIEW,
    TENANT_UPDATE,
    TENANT_DELETE,
    /** Reservada para F6. */
    TENANT_BILLING,
    TENANT_AUDIT_VIEW,

    // §6.2 Membros
    MEMBER_VIEW,
    MEMBER_INVITE,
    MEMBER_UPDATE_ROLE,
    MEMBER_SUSPEND,
    MEMBER_REMOVE,

    // §6.3 Clientes
    CLIENT_VIEW,
    CLIENT_CREATE,
    CLIENT_UPDATE,
    CLIENT_DELETE,

    // §6.4 Contratos e períodos
    CONTRACT_VIEW,
    CONTRACT_CREATE,
    CONTRACT_UPDATE,
    CONTRACT_DELETE,
    CONTRACT_TRANSITION,
    CONTRACT_VIEW_FINANCIAL,
    PERIOD_VIEW,
    PERIOD_CLOSE,
    PERIOD_REOPEN,
    PERIOD_ADJUST,

    // §6.5 Tickets
    TICKET_VIEW,
    TICKET_CREATE,
    TICKET_UPDATE_OWN,
    TICKET_UPDATE_ANY,
    TICKET_ASSIGN,
    TICKET_TRANSITION,
    TICKET_DELETE,

    // §6.6 Work logs e timer
    WORKLOG_VIEW_OWN,
    WORKLOG_VIEW_ANY,
    WORKLOG_CREATE,
    WORKLOG_CREATE_FOR_OTHER,
    WORKLOG_UPDATE_OWN,
    WORKLOG_UPDATE_ANY,
    WORKLOG_DELETE_OWN,
    WORKLOG_DELETE_ANY,
    WORKLOG_UPDATE_LOCKED,
    TIMER_USE,
    TIMER_VIEW_ANY,
    TIMER_STOP_ANY,

    // §6.7 Categorias e tags
    CATEGORY_VIEW,
    CATEGORY_MANAGE,
    TAG_VIEW,
    TAG_MANAGE,

    // §6.8 Comentários e anexos
    COMMENT_VIEW,
    COMMENT_CREATE,
    COMMENT_UPDATE_OWN,
    COMMENT_DELETE_ANY,
    ATTACHMENT_VIEW,
    ATTACHMENT_UPLOAD,
    ATTACHMENT_DELETE_OWN,
    ATTACHMENT_DELETE_ANY,

    // §6.9 Relatórios e notificações
    REPORT_VIEW_OWN,
    REPORT_VIEW_ANY,
    REPORT_EXPORT,
    DASHBOARD_VIEW_OWN,
    DASHBOARD_VIEW_ANY,
    NOTIFICATION_VIEW
}
