package com.devtime.shared.error;

import java.time.Duration;
import java.util.Map;
import lombok.Getter;

/**
 * Limite de requisições excedido (ART-073, §8.1 de security.md).
 *
 * <p>Carrega o tempo de espera porque a resposta precisa do header {@code Retry-After}: sem ele, o
 * cliente só pode tentar de novo às cegas, o que transforma um limite protetivo em fonte de novas
 * tentativas — exatamente o oposto do efeito pretendido.
 */
@Getter
public class RateLimitExceededException extends BusinessRuleException {

    private final transient Duration retryAfter;

    private RateLimitExceededException(String scope, Duration retryAfter) {
        super(
                ErrorCode.RATE_LIMIT_EXCEEDED,
                // O escopo identifica qual limite foi atingido, sem revelar contadores nem o valor
                // que compõe a chave (IP ou e-mail).
                Map.of("scope", scope, "retryAfterSeconds", retryAfter.toSeconds()),
                "Limite de requisições excedido: " + scope);
        this.retryAfter = retryAfter;
    }

    public static RateLimitExceededException of(String scope, Duration retryAfter) {
        return new RateLimitExceededException(scope, retryAfter);
    }
}
