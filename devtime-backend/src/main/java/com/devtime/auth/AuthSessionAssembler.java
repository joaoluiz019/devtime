package com.devtime.auth;

import com.devtime.auth.dto.AuthResponses.AuthenticatedTenant;
import com.devtime.auth.dto.AuthResponses.AuthenticatedUser;
import com.devtime.auth.dto.AuthResponses.SessionResponse;
import com.devtime.auth.dto.AuthResponses.TenantOptionResponse;
import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.security.JwtService;
import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.tenant.dto.TenantViews.MembershipView;
import com.devtime.tenant.dto.TenantViews.TenantOption;
import com.devtime.tenant.dto.TenantViews.TenantView;
import com.devtime.user.dto.UserAccount;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Montagem da resposta de sessão (spec 001 §23, {@code authentication.md} §5.3).
 *
 * <p>Concentra a emissão do access token e a derivação de permissões em um ponto só, porque quatro
 * endpoints — login, refresh, select-tenant e verify-email — devolvem exatamente a mesma estrutura.
 * Montá-la em cada um deles produziria quatro oportunidades de divergência no contrato.
 *
 * <p>TK-03 / BR-163: as permissões são <b>derivadas do papel</b> a cada montagem e devolvidas no
 * corpo. Elas não viajam no token: se viajassem, o rebaixamento de um {@code ADMIN} só teria efeito
 * quando o access token expirasse.
 */
@Component
@RequiredArgsConstructor
public class AuthSessionAssembler {

    private final JwtService jwtService;
    private final DevTimeProperties properties;

    /** Sessão completa: token com {@code tid}, papel e permissões (§5.3, um único tenant). */
    public SessionResponse withTenant(
            UserAccount account, TenantView tenant, MembershipView membership) {
        String accessToken =
                jwtService.issueAccessToken(
                        account.id(),
                        tenant.id(),
                        membership.id(),
                        membership.role(),
                        tenant.timezone());
        return new SessionResponse(
                accessToken,
                SessionResponse.BEARER,
                properties.security().accessTokenTtl().toSeconds(),
                false,
                toUser(account),
                toTenant(tenant),
                membership.role(),
                permissionsOf(membership.role()),
                null);
    }

    /**
     * Sessão de pré-seleção: token sem {@code tid} (§5.3, múltiplos tenants).
     *
     * <p>CE-P-11: com este token, apenas {@code /auth/tenants} e {@code /auth/select-tenant}
     * respondem; qualquer outro endpoint devolve {@code 401 DEVTIME-1002}.
     */
    public SessionResponse pendingTenantSelection(UserAccount account, List<TenantOption> options) {
        return new SessionResponse(
                jwtService.issuePreAuthToken(account.id()),
                SessionResponse.BEARER,
                properties.security().accessTokenTtl().toSeconds(),
                true,
                toUser(account),
                null,
                null,
                null,
                toOptions(options));
    }

    public AuthenticatedUser toUser(UserAccount account) {
        return new AuthenticatedUser(
                account.id(),
                account.fullName(),
                account.displayName(),
                account.email(),
                account.avatarUrl());
    }

    public AuthenticatedTenant toTenant(TenantView tenant) {
        return new AuthenticatedTenant(
                tenant.id(),
                tenant.name(),
                tenant.slug(),
                tenant.timezone(),
                tenant.currency(),
                tenant.logoUrl());
    }

    public List<TenantOptionResponse> toOptions(List<TenantOption> options) {
        return options.stream()
                .map(
                        option ->
                                new TenantOptionResponse(
                                        option.id(),
                                        option.name(),
                                        option.slug(),
                                        option.role(),
                                        option.logoUrl(),
                                        option.status().name()))
                // Ordem estável por nome: a lista de seleção é uma tela de escolha, e uma ordem
                // que muda entre chamadas faz o usuário clicar na organização errada.
                .sorted(Comparator.comparing(TenantOptionResponse::name))
                .toList();
    }

    /** §7 de permissions.md: o conjunto exato do papel, em ordem estável. */
    public List<String> permissionsOf(Role role) {
        return RolePermissions.of(role).stream().map(Permission::name).sorted().toList();
    }
}
