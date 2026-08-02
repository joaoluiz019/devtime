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
 * @param userId claim {@code sub}; nulo <b>apenas</b> em sessão de job de plataforma, criada por
 *     {@link #system(UUID, Role, Set)}. Em requisição autenticada o filtro sempre o preenche, e
 *     {@code requireUserId()} falha alto se algum caminho tentar operar sem ele
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
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /**
     * Sessão de um job de plataforma, sem usuário (BR-049, JB-06).
     *
     * <p>{@code userId} nulo é a verdade e não uma lacuna: nenhuma pessoa pediu a transição, e é o
     * que faz a trilha registrar {@code actorType = SYSTEM} (CE-S-06). Um identificador sintético
     * apareceria na auditoria como se alguém tivesse agido.
     *
     * <p>O papel serve apenas à verificação de permissão dos serviços chamados pelo job, que é o
     * mesmo caminho da operação manual — duplicar o serviço sem {@code @PreAuthorize} criaria dois
     * caminhos para a mesma regra.
     *
     * @param role papel assumido; {@code OWNER} nos jobs, que executam operações de organização
     */
    public static TenantSession system(UUID tenantId, Role role, Set<Permission> permissions) {
        return new TenantSession(null, tenantId, null, role, permissions, null);
    }

    public boolean hasTenantSelected() {
        return tenantId != null;
    }
}
