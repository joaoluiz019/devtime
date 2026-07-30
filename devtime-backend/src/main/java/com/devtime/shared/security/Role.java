package com.devtime.shared.security;

/**
 * Papéis atribuíveis a um {@code Membership} (permissions.md §5, glossário §9).
 *
 * <p>A hierarquia de capacidade é {@code OWNER ⊇ ADMIN ⊇ MANAGER ⊇ MEMBER ⊇ VIEWER}, mas a inclusão
 * não é absoluta: {@code MEMBER} possui permissões de ownership sobre os próprios registros que
 * {@code VIEWER} não possui, e {@code MANAGER} não herda gestão de membros. Por isso o modelo
 * verifica <b>permissões atômicas</b>, nunca papéis — o que também viabiliza papéis customizados em
 * F6 sem quebrar o código (permissions.md §14).
 */
public enum Role {
    OWNER,
    ADMIN,
    MANAGER,
    MEMBER,
    VIEWER,
    /** Acesso externo do cliente, em leitura. Reservado para v2.x (permissions.md §5). */
    CLIENT_PORTAL
}
