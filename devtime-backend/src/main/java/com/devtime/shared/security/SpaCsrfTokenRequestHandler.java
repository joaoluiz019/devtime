package com.devtime.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Handler de CSRF para SPA (Spring Security, "Integrating with Single-Page Applications").
 *
 * <p>O padrão do Spring Security 6 é {@link XorCsrfTokenRequestAttributeHandler}: o valor devolvido
 * pelo cliente precisa vir <b>mascarado</b> (XOR com um sal aleatório, proteção contra BREACH).
 * Ocorre que {@code CookieCsrfTokenRepository} grava no cookie {@code XSRF-TOKEN} o valor
 * <b>cru</b>, e é esse valor que o navegador — Angular, axios, qualquer cliente — copia de volta
 * para o header. Cru contra mascarado nunca casa, e toda requisição de escrita respondia {@code
 * 403} (DEVTIME-1105), com a mensagem enganosa de sessão expirada.
 *
 * <p>A composição abaixo resolve os dois lados: a escrita continua mascarada (o token nunca aparece
 * cru em corpo de resposta, então BREACH segue coberto), enquanto a leitura aceita o valor cru
 * quando ele chega pelo header — o único caminho que a SPA usa. Parâmetro de formulário continua
 * exigindo o valor mascarado.
 *
 * <p>{@code csrfToken.get()} em {@link #handle} materializa o token deferido em toda requisição,
 * garantindo que o cookie exista antes da primeira escrita.
 */
@Component
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return this.plain.resolveCsrfTokenValue(request, csrfToken);
        }
        return this.xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
