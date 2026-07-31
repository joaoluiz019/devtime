package com.devtime.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Métricas da feature 001 (spec §29, T-001-69).
 *
 * <p>Reunidas em um componente para que a lista de §29 seja verificável por leitura, em vez de
 * espalhada por chamadas soltas a {@code MeterRegistry} dentro dos serviços.
 *
 * <p>{@code auth.token.reuse_detected} e {@code auth.account.locked} são as duas com alerta
 * declarado: a primeira em <b>qualquer</b> ocorrência, com severidade crítica; a segunda acima de
 * dez por hora.
 */
@Component
@RequiredArgsConstructor
public class AuthMetrics {

    private final MeterRegistry registry;

    /** {@code auth.register.total} com tag {@code outcome}. */
    public void registerAttempt(String outcome) {
        counter("auth.register.total", outcome).increment();
    }

    /** {@code auth.login.total} — {@code success}, {@code bad_credentials}, {@code locked}, … */
    public void loginAttempt(String outcome) {
        counter("auth.login.total", outcome).increment();
    }

    /** {@code auth.refresh.total} com tag {@code outcome}. */
    public void refreshAttempt(String outcome) {
        counter("auth.refresh.total", outcome).increment();
    }

    /** {@code auth.token.reuse_detected}: alerta crítico em qualquer ocorrência (RN-005). */
    public void tokenReuseDetected() {
        registry.counter("auth.token.reuse_detected").increment();
    }

    /** {@code auth.account.locked}: acima de 10/h indica ataque em andamento. */
    public void accountLocked() {
        registry.counter("auth.account.locked").increment();
    }

    /** {@code auth.email.send_failures} com tag {@code type}. */
    public void emailSendFailure(String type) {
        registry.counter("auth.email.send_failures", "type", type).increment();
    }

    private Counter counter(String name, String outcome) {
        return registry.counter(name, "outcome", outcome);
    }
}
