package com.devtime.shared.security;

import com.devtime.shared.config.DevTimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Codificador de senha, isolado de {@link SecurityConfig}.
 *
 * <p>Separado por necessidade estrutural, não por organização. {@code SecurityConfig} depende do
 * {@code TenantContextFilter}, que depende de {@code SessionValidationService} e, por ele, de
 * serviços de feature. Qualquer serviço de feature que precise do {@link PasswordEncoder} — a
 * verificação de senha do cancelamento de organização (SG-04) é o primeiro caso — fecharia um ciclo
 * de criação de beans com a configuração de segurança HTTP.
 *
 * <p>Manter o bean aqui torna o codificador dependente apenas de configuração, que é o que ele de
 * fato precisa.
 */
@Configuration
@RequiredArgsConstructor
public class PasswordEncoderConfiguration {

    private final DevTimeProperties properties;

    /** ART-081 / PW-01: BCrypt custo 12. O custo vem de configuração validada (CF-01). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(properties.security().bcryptStrength());
    }
}
