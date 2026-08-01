package com.devtime.worklog;

import com.devtime.worklog.domain.WorkLog;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detecção de violações de invariante em {@code work_logs} (INV-WKL-05, INV-WKL-08).
 *
 * <p><b>Detecta e alerta; nunca corrige</b> (CP-17, OB-07). Corrigir uma sobreposição exigiria
 * escolher qual dos dois registros truncar ou excluir — uma decisão sobre horas faturáveis que o
 * sistema não tem autoridade para tomar (PR-03). E corrigir automaticamente esconderia o defeito:
 * uma sobreposição que chegou ao banco significa que a validação de RN-102 <b>falhou</b>, e é isso
 * que precisa ser investigado.
 *
 * <p>É a terceira camada de defesa de RN-102. Como não existe constraint {@code EXCLUDE} para ela
 * (OB-02) — colidiria com o soft delete —, este job é o que descobre o que a validação e o índice
 * deixaram passar (R-01).
 *
 * <p>Os dois eventos que ele registra são {@code ERROR} com acionamento operacional imediato:
 * significam que uma invariante foi violada no banco.
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class WorkLogConsistencyJob {

    private final WorkLogRepository repository;

    @Scheduled(cron = "0 15 2 * * *")
    @SchedulerLock(name = "workLogConsistency", lockAtMostFor = "PT30M")
    @Transactional(readOnly = true)
    public void detectViolations() {
        // Somente leitura, e a anotação torna isso verificável: um job que não pode escrever não
        // pode corrigir, nem por engano.
        List<WorkLog> overlapping = repository.findOverlappingPairs();

        for (WorkLog workLog : overlapping) {
            // §28: identificadores e intervalo; nunca a descrição (CP-18).
            log.error(
                    "INV-WKL-05 violada: sobreposição persistida workLogId={} userId={}"
                            + " startedAt={} endedAt={}",
                    workLog.getId(),
                    workLog.getUserId(),
                    workLog.getStartedAt(),
                    workLog.getEndedAt());
        }

        if (overlapping.isEmpty()) {
            log.info("verificação de consistência de work logs concluída sem violações");
        } else {
            log.error(
                    "verificação de consistência encontrou sobreposições persistidas quantidade={}",
                    overlapping.size());
        }
    }
}
