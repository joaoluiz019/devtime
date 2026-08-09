package com.devtime.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emite o cookie {@code XSRF-TOKEN} em toda requisição.
 *
 * <p>Desde o Spring Security 6 o token é <b>diferido</b>: {@code CookieCsrfTokenRepository} só
 * grava o cookie quando alguém lê o valor do token. Em uma API stateless consumida por SPA, ninguém
 * lê — não há página renderizada no servidor com um campo oculto. O resultado é que o cookie nascia
 * apenas como efeito colateral da primeira requisição <i>recusada</i>, e o cliente precisava falhar
 * uma vez para poder acertar na seguinte.
 *
 * <p>Chamar {@link CsrfToken#getToken()} aqui força a gravação de forma determinística. O filtro
 * não enfraquece nada: o valor já seria entregue ao mesmo cliente, e a proteção continua sendo a
 * mesma — o atacante em outro domínio não consegue <i>ler</i> o cookie para copiá-lo no header.
 */
@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
