package com.devtime.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Segurança de método ({@code @PreAuthorize}), isolada de {@link SecurityConfig}.
 *
 * <p>BR-161 exige a verificação de permissão na camada de serviço, e o advisor que a aplica precisa
 * do {@code MethodSecurityExpressionHandler}. Enquanto esse bean vivia em {@link SecurityConfig},
 * qualquer serviço alcançado pela cadeia de filtros HTTP e anotado com {@code @PreAuthorize}
 * fechava um ciclo: {@code SecurityConfig → TenantContextFilter → SessionValidationService →
 * TenantService → advisor → SecurityConfig}. {@code TenantServiceImpl} passou a ser exatamente esse
 * caso ao ganhar as permissões de {@code TENANT_VIEW}/{@code TENANT_UPDATE}.
 *
 * <p>Separar as duas configurações resolve na raiz: a segurança de método depende apenas do
 * avaliador de permissões, e a de HTTP apenas dos filtros.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class MethodSecurityConfiguration {

    private final PermissionEvaluator permissionEvaluator;

    /** Registra {@link DevTimePermissionEvaluator} para uso em {@code hasPermission(...)}. */
    @Bean
    DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler =
                new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
