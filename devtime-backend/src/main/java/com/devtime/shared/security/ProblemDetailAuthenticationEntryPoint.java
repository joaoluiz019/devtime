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
        write(request, response, ErrorCode.PERMISSION_DENIED);
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
