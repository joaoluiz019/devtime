package com.devtime.shared.tenancy;

import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import java.util.Set;
import java.util.UUID;

/**
 * Dados da sessão autenticada, derivados exclusivamente das claims do access token.
 *
 * <p>{@code tenantId}, {@code role} e {@code membershipId} são nulos no <i>token de
 * pré-seleção</i>, emitido quando o usuário pertence a mais de um tenant e ainda não escolheu um
 * ({@code security.md} §3). Nesse estado apenas os endpoints de seleção de tenant respondem
 * (CE-S-05, CE-P-11).
 *
 * @param userId claim {@code sub}
 * @param tenantId claim {@code tid}; nulo antes da seleção de tenant
 * @param membershipId claim {@code mid}; nulo antes da seleção de tenant
 * @param role claim {@code role}; nulo antes da seleção de tenant
 * @param permissions derivadas do papel a cada requisição, nunca lidas do token (TK-03, AZ-02)
 * @param timezone claim {@code tz}, usada para formatação no fuso do tenant (ART-032)
 */
public record TenantSession(
        UUID userId,
        UUID tenantId,
        UUID membershipId,
        Role role,
        Set<Permission> permissions,
        String timezone) {

    public TenantSession {
        if (userId == null) {
            throw new IllegalArgumentException("TenantSession exige userId");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasTenantSelected() {
        return tenantId != null;
    }
}
