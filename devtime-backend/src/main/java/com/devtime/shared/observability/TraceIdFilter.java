package com.devtime.shared.observability;

import com.devtime.shared.persistence.UuidGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Atribui um {@code traceId} a cada requisição e o publica no MDC.
 *
 * <p>ER-07 / EH-02: toda exceção e todo erro carregam {@code traceId}. Executa antes de tudo
 * ({@link Ordered#HIGHEST_PRECEDENCE}) para que até falhas de autenticação sejam correlacionáveis.
 *
 * <p>O identificador é gerado no servidor e não aceito do cliente: um {@code traceId} controlado
 * por quem chama permitiria poluir ou forjar a correlação de logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String RESPONSE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UuidGenerator.newId().toString().replace("-", "");
        MDC.put(TraceContext.TRACE_ID, traceId);
        response.setHeader(RESPONSE_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Thread de pool reutilizada carregaria o traceId da requisição anterior.
            MDC.remove(TraceContext.TRACE_ID);
        }
    }
}
