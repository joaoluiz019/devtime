package com.devtime.shared.security;

import com.devtime.shared.tenancy.TenantContext;
import java.io.Serializable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Avaliador de permissões usado por {@code @PreAuthorize("hasPermission(null, 'X')")} (security.md
 * §7.2).
 *
 * <p>AZ-02 / TK-03: a permissão é resolvida a partir do papel presente no {@link TenantContext} da
 * requisição, não de authorities gravadas no token. O parâmetro {@code authentication} é
 * deliberadamente ignorado por isso.
 *
 * <p>Ownership (permissions.md §8) e escopo de dados (§9) <b>não</b> são avaliados aqui: AZ-03
 * exige que ownership seja verificado no serviço, após a permissão, e IMP-02 exige que o escopo
 * seja aplicado na consulta. Um avaliador que tentasse decidir ownership precisaria carregar o
 * recurso, o que transformaria uma anotação de autorização em acesso a dados.
 */
@Component
@RequiredArgsConstructor
public class DevTimePermissionEvaluator implements PermissionEvaluator {

    private final TenantContext tenantContext;

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
        return resolve(permission)
                .map(required -> tenantContext.currentPermissions().contains(required))
                .orElse(false);
    }

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Serializable targetId,
            String targetType,
            Object permission) {
        return hasPermission(authentication, null, permission);
    }

    /**
     * Converte o argumento da anotação em {@link Permission}.
     *
     * <p>Um nome desconhecido retorna vazio, o que nega o acesso. Negar é o comportamento correto
     * para um erro de digitação em {@code @PreAuthorize} (ART-085): conceder seria transformar um
     * erro de programação em falha de segurança. O teste de arquitetura é o que impede o erro
     * chegar à branch principal.
     */
    private java.util.Optional<Permission> resolve(Object permission) {
        if (permission == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Permission.valueOf(permission.toString()));
        } catch (IllegalArgumentException unknown) {
            return java.util.Optional.empty();
        }
    }
}
