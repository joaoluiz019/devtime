package com.devtime.dashboard.domain;

/**
 * Abrangência dos dados do painel (CP-01 de specs/010, §9 de permissions.md).
 *
 * <p>Não é preferência do usuário nem parâmetro da requisição: é derivada do papel. Aceitá-la do
 * cliente permitiria a um {@code MEMBER} pedir {@code TENANT} e ler as horas dos colegas.
 */
public enum DashboardScope {

    /** Papéis com {@code DASHBOARD_VIEW_ANY}: todo o tenant. */
    TENANT,

    /** {@code MEMBER}: apenas os próprios registros e os contratos vinculados. */
    USER
}
