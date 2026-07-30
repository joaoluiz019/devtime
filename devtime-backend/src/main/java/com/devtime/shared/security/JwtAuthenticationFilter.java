package com.devtime.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida o access token e popula o {@code SecurityContext} (backend.md §7.2, security.md §7.1).
 *
 * <p>O filtro <b>não</b> rejeita a requisição quando o token está ausente ou inválido: apenas deixa
 * o contexto vazio e segue a cadeia. Quem nega é a regra {@code anyRequest().authenticated()} do
 * {@code SecurityConfig}, garantindo que o comportamento padrão seja negar (ART-085) sem que este
 * filtro precise conhecer a allowlist pública — se conhecesse, existiriam duas fontes de verdade
 * sobre o que é público.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            AccessTokenClaims claims = jwtService.parse(token);
            var authentication =
                    new UsernamePasswordAuthenticationToken(
                            claims,
                            null,
                            claims.role() == null
                                    ? List.of()
                                    : List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidAccessTokenException e) {
            // security.md §14: token malformado ou com assinatura inválida é registrado em WARN.
            // Apenas o motivo interno é logado; nenhum trecho do token vai para o log (§9.2).
            log.warn("Access token rejeitado. reason={}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
