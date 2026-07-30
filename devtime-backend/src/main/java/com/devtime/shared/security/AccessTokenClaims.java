package com.devtime.shared.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Claims do access token, já validadas e tipadas (security.md §5.2).
 *
 * <p>TK-06: nenhum dado sensível — e-mail, nome ou documento — trafega aqui. Um JWT é apenas
 * codificado, não criptografado, e qualquer portador consegue lê-lo.
 *
 * <p>TK-03: a lista de permissões <b>não</b> é uma claim. Incluí-la a congelaria por 15 minutos, e
 * um {@code ADMIN} rebaixado a {@code MEMBER} manteria privilégios administrativos até o token
 * expirar. As permissões são derivadas de {@link #role} a cada requisição.
 *
 * @param userId claim {@code sub}
 * @param tenantId claim {@code tid}; nula no token de pré-seleção
 * @param membershipId claim {@code mid}; nula no token de pré-seleção
 * @param role claim {@code role}; nula no token de pré-seleção
 * @param timezone claim {@code tz}; opcional
 * @param tokenId claim {@code jti}, usada para revogação pontual e para log — é o único
 *     identificador de token que pode aparecer em log (security.md §9.2)
 * @param issuedAt claim {@code iat}
 * @param expiresAt claim {@code exp}
 */
public record AccessTokenClaims(
        UUID userId,
        UUID tenantId,
        UUID membershipId,
        Role role,
        String timezone,
        UUID tokenId,
        Instant issuedAt,
        Instant expiresAt) {

    public boolean hasTenantSelected() {
        return tenantId != null && role != null;
    }
}
