package com.devtime.timer;

import com.devtime.audit.AuditService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro da tentativa frustrada de encerramento (§18, {@code TIMER_STOP_FAILED}).
 *
 * <p><b>Por que é uma classe separada:</b> o registro precisa de {@code REQUIRES_NEW}, e Spring
 * aplica propagação por proxy — uma chamada a {@code this.audit(...)} dentro de {@code
 * TimerServiceImpl} não passaria pelo proxy e herdaria a transação principal, que é exatamente a
 * que será revertida. O registro desapareceria junto com o rollback que preserva o cronômetro.
 *
 * <p>CE-B-02 / BR-122: {@code REQUIRES_NEW} é permitido justamente neste caso — registro de falha.
 * A justificativa é que a informação só tem valor se sobreviver ao rollback.
 *
 * <p>Um usuário que tenta encerrar cinco vezes e falha em todas está perdendo tempo real de
 * trabalho e prestes a desistir e descartar. Isso é métrica de produto, não apenas de erro.
 */
@Component
@RequiredArgsConstructor
public class TimerFailureAuditor {

    private final AuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStopFailure(UUID timerId, String errorCode, int elapsedSeconds) {
        auditService.record(
                "TIMER_STOP_FAILED",
                "Timer",
                timerId,
                Map.of(),
                Map.of(),
                // §28: código e tempo decorrido; nunca a descrição.
                Map.of("errorCode", errorCode, "elapsedSeconds", elapsedSeconds));
    }
}
