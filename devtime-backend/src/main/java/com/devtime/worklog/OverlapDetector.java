package com.devtime.worklog;

import com.devtime.worklog.domain.WorkLog;
import com.devtime.worklog.domain.WorkLogExceptions;
import com.devtime.worklog.domain.WorkLogInterval;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Detecção de sobreposição de sessões do mesmo usuário (RN-102, INV-WKL-05).
 *
 * <p>É a validação mais crítica da feature. Sobreposição não detectada significa a <b>mesma hora
 * cobrada duas vezes</b> — a falha que destrói a confiança do cliente de forma irrecuperável
 * (RP-01), e o principal motivo de a complexidade de {@code 008} ser classificada como crítica.
 *
 * <p><b>A garantia é da aplicação, não do banco</b> (OB-02, RS-05). PostgreSQL suporta {@code
 * EXCLUDE USING gist}, e é assim que a não-sobreposição de períodos é garantida em V013. Aqui a
 * constraint colide com o soft delete: um registro excluído logicamente permanece na tabela e
 * bloquearia o intervalo, impedindo o usuário de recriar um registro que ele mesmo apagou. Restam
 * três camadas de defesa — esta validação, {@code idx_work_logs_overlap} e {@code
 * WorkLogConsistencyJob} com alerta crítico.
 *
 * <p><b>Escopo da verificação</b> (§6.2):
 *
 * <ul>
 *   <li>Apenas o <b>mesmo</b> {@code userId} — duas pessoas no mesmo ticket ao mesmo tempo são
 *       permitidas (CX-08), porque a regra é "uma pessoa não trabalha em duas coisas ao mesmo
 *       tempo", não "um ticket não recebe duas pessoas".
 *   <li>Registros excluídos são ignorados (pelo {@code @SQLRestriction} da entidade).
 *   <li>Na edição, o próprio identificador é excluído da comparação (CX-17).
 *   <li>Restrita ao tenant corrente — limitação conhecida e declarada em OB-03/RS-04.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OverlapDetector {

    private final WorkLogRepository repository;

    /**
     * @param excludeId próprio identificador na edição; {@code null} na criação
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2102} / {@code 422},
     *     com o registro conflitante nos detalhes
     */
    public void assertNoOverlap(UUID userId, WorkLogInterval interval, UUID excludeId) {
        findConflict(userId, interval, excludeId)
                .ifPresent(
                        conflict -> {
                            // §28: intervalo e identificadores, nunca a descrição (CP-18).
                            log.info(
                                    "sobreposição rejeitada userId={} startedAt={} endedAt={}"
                                            + " conflictingWorkLogId={}",
                                    userId,
                                    interval.startedAt(),
                                    interval.endedAt(),
                                    conflict.getId());
                            throw WorkLogExceptions.overlap(
                                    conflict.getId(),
                                    conflict.getStartedAt(),
                                    conflict.getEndedAt());
                        });
    }

    /**
     * Registro conflitante, sem lançar.
     *
     * <p>Serve a {@code POST /work-logs/validate} (FA-01), que precisa <b>relatar</b> o conflito
     * sem interromper a montagem do restante da prévia — o usuário deve ver, na mesma resposta, a
     * sobreposição e o saldo resultante.
     */
    public Optional<WorkLog> findConflict(UUID userId, WorkLogInterval interval, UUID excludeId) {
        return repository
                .findFirstOverlappingId(userId, interval.startedAt(), interval.endedAt(), excludeId)
                .flatMap(repository::findById);
    }
}
