package com.devtime.shared.observability;

import org.slf4j.MDC;

/** Acesso ao {@code traceId} da requisição corrente. */
public final class TraceContext {

    public static final String TRACE_ID = "traceId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";

    private TraceContext() {}

    /**
     * Retorna o {@code traceId} corrente, ou vazio se não houver.
     *
     * <p>Nunca gera um valor aqui: um {@code traceId} inventado no momento da resposta não estaria
     * em nenhuma linha de log, e o suporte não conseguiria recuperar a requisição (EX-06 de
     * ADR-017). A geração pertence ao {@link TraceIdFilter}, na borda.
     */
    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID);
        return traceId == null ? "" : traceId;
    }
}
