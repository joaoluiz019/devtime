package com.devtime.auth;

import com.devtime.shared.config.DevTimeProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie de refresh (§5.4 de security.md, §5.3 de authentication.md, TS-001-19).
 *
 * <p>Atributos obrigatórios e o porquê de cada um:
 *
 * <ul>
 *   <li>{@code HttpOnly} — SG-06: inacessível a JavaScript, portanto não exfiltrável por XSS. É o
 *       motivo de o refresh token não viver em {@code localStorage} (CP-03).
 *   <li>{@code Secure} — impede o envio em texto claro. Navegadores tratam {@code localhost} como
 *       contexto seguro, então o desenvolvimento local não precisa de exceção.
 *   <li>{@code SameSite=Strict} — SG-07: o cookie não acompanha requisições originadas de outro
 *       site, fechando o CSRF sobre o endpoint de renovação.
 *   <li>{@code Path=/api/v1/auth} — o cookie sequer é enviado nas demais rotas da API, reduzindo a
 *       superfície de exposição a um único prefixo.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

    static final String COOKIE_NAME = "dt_refresh";
    static final String COOKIE_PATH = "/api/v1/auth";

    private final DevTimeProperties properties;

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(properties.security().refreshTokenTtl()).build();
    }

    /**
     * Cookie de remoção, enviado no logout.
     *
     * <p>Valor vazio e {@code Max-Age=0}: os demais atributos precisam ser idênticos aos da
     * criação, porque o navegador só substitui um cookie quando nome, domínio e caminho coincidem.
     */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
