package com.devtime.dashboard;

import com.devtime.dashboard.domain.DashboardScope;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolve a abrangência dos dados pelo papel (CP-01 de specs/010, §9 de permissions.md).
 *
 * <p>O escopo é <b>derivado</b>, nunca recebido: nenhum parâmetro da requisição o influencia. É o
 * que impede que um {@code MEMBER} peça {@code TENANT} e leia as horas dos colegas (SG-02).
 *
 * <p>A permissão consultada é {@code DASHBOARD_VIEW_ANY}, e não {@code WORKLOG_VIEW_ANY}: são
 * concedidas aos mesmos papéis hoje, mas a pergunta que esta classe responde é sobre o painel. Uma
 * futura divergência na matriz deve alterar o painel apenas se for sobre o painel.
 */
@Component
@RequiredArgsConstructor
public class DashboardScopeResolver {

    private final TenantContext tenantContext;

    public DashboardScope resolve() {
        return tenantContext.currentPermissions().contains(Permission.DASHBOARD_VIEW_ANY)
                ? DashboardScope.TENANT
                : DashboardScope.USER;
    }

    /**
     * Valores monetários não são expostos por nenhum campo do painel (INV-DSH-04).
     *
     * <p>Publicado ainda assim porque §16 declara a permissão como parte do contrato da feature: se
     * um campo monetário for acrescentado, esta é a verificação que ele deve consultar — e mantê-la
     * aqui evita que a próxima pessoa a acrescentá-lo precise redescobrir a regra.
     */
    public boolean canViewFinancial() {
        return tenantContext.currentPermissions().contains(Permission.CONTRACT_VIEW_FINANCIAL);
    }
}
