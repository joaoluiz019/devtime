package com.devtime.shared.security;

import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Traduz falhas de autenticação e autorização da cadeia de filtros em Problem Details.
 *
 * <p>Necessário porque essas falhas ocorrem <b>antes</b> de chegar a um controller, logo fora do
 * alcance do {@code GlobalExceptionHandler}. Sem isto, o Spring Security responderia com seu
 * formato padrão e a API teria dois formatos de erro — quebrando ART-072.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthenticationEntryPoint
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemDetailFactory factory;
    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        write(request, response, ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException accessDeniedException)
            throws IOException {
        write(request, response, codeFor(accessDeniedException));
    }

    /**
     * Distingue falha de CSRF de falta de permissão.
     *
     * <p>As duas chegam aqui como {@code AccessDeniedException} e, até esta correção, produziam a
     * mesma resposta: "Você não tem permissão para esta ação". A orientação que essa frase dá está
     * errada para metade dos casos — quando o token CSRF não veio, não há papel a conceder nem
     * administrador a procurar, e a pessoa fica presa relendo a tela de permissões. O sintoma é
     * característico: toda leitura funciona e <b>toda</b> alteração falha, porque só as mutações
     * passam pelo {@code CsrfFilter}.
     */
    private ErrorCode codeFor(org.springframework.security.access.AccessDeniedException exception) {
        return exception instanceof org.springframework.security.web.csrf.CsrfException
                ? ErrorCode.CSRF_TOKEN_INVALID
                : ErrorCode.PERMISSION_DENIED;
    }

    private void write(
            HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        ProblemDetail problem = factory.create(errorCode, request);
        response.setStatus(errorCode.getDefaultStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Respostas de API nunca são cacheadas (security.md §8.2).
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
