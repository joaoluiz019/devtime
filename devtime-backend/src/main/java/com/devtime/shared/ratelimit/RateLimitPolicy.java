package com.devtime.shared.ratelimit;

import java.time.Duration;

/**
 * Limites de §8.1 de {@code security.md}.
 *
 * <p>Reunidos em um enum para que a tabela normativa seja verificável por leitura: cada constante
 * corresponde a uma linha do documento, e um limite novo sem linha correspondente fica visível na
 * revisão.
 *
 * @param scope identificador do limite, usado na chave do contador e na resposta de erro
 */
public enum RateLimitPolicy {

    /** Login por IP + e-mail: 10 por minuto (AU-04). */
    LOGIN("login", 10, Duration.ofMinutes(1)),

    /** Registro por IP: 5 por hora (SG-14). */
    REGISTER("register", 5, Duration.ofHours(1)),

    /** Redefinição de senha por e-mail: 3 por hora. */
    PASSWORD_RESET("forgot-password", 3, Duration.ofHours(1)),

    /** Reenvio de verificação por e-mail: 3 por hora. */
    RESEND_VERIFICATION("resend-verification", 3, Duration.ofHours(1));

    private final String scope;
    private final int limit;
    private final Duration window;

    RateLimitPolicy(String scope, int limit, Duration window) {
        this.scope = scope;
        this.limit = limit;
        this.window = window;
    }

    public String scope() {
        return scope;
    }

    public int limit() {
        return limit;
    }

    public Duration window() {
        return window;
    }
}
