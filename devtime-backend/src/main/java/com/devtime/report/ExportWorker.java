package com.devtime.report;

import com.devtime.audit.AuditService;
import com.devtime.report.ExportGenerator.GeneratedFile;
import com.devtime.report.domain.ExportStatus;
import com.devtime.report.domain.ReportExecution;
import com.devtime.report.dto.ExportRequests.ExportOptions;
import com.devtime.report.dto.ExportRequests.ExportParameters;
import com.devtime.report.event.ExportEvents.ExportCompletedEvent;
import com.devtime.report.event.ExportEvents.ExportFailedEvent;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.storage.StoragePort;
import com.devtime.shared.time.TenantClock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processamento assíncrono de uma exportação (§22.2, {@code ExportWorkerImpl} de specs/012).
 *
 * <p>Uma execução por transação, deliberadamente. Se as execuções de um lote compartilhassem
 * transação, a falha da última desfaria as anteriores — e uma exportação concluída, com o binário
 * já gravado no storage, voltaria a {@code QUEUED} apontando para um arquivo que ninguém mais
 * referencia.
 *
 * <p>CE-R-11 e RNF-025: a falha de uma exportação não afeta nenhum outro fluxo. Ela vira {@code
 * FAILED} com o motivo, e o solicitante é notificado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportWorker {

    private static final String ENTITY_TYPE = "ReportExecution";

    /**
     * §8.3 e BR-092: o que chega ao usuário é isto, nunca a mensagem da exceção — ela pode carregar
     * SQL, nome de tabela ou trecho de descrição de work log (CP-18).
     */
    private static final String GENERIC_FAILURE = "Falha ao gerar o arquivo da exportação";

    private final ReportExecutionRepository repository;
    private final ExportGenerator generator;
    private final StoragePort storage;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final ObjectMapper objectMapper;
    private final TenantClock clock;

    /**
     * Assume e processa uma execução.
     *
     * <p>BR-185: idempotente por convergência. Uma execução que já saiu de {@code QUEUED}/{@code
     * FAILED} é ignorada — é o que impede que duas instâncias do worker gerem o mesmo arquivo duas
     * vezes se o {@code @SchedulerLock} for perdido.
     *
     * @return verdadeiro quando a execução foi processada até um estado terminal
     */
    @Transactional
    public boolean process(java.util.UUID executionId) {
        ReportExecution execution = repository.findById(executionId).orElse(null);
        if (execution == null || !execution.getStatus().isPendingWork()) {
            return false;
        }
        if (!execution.hasAttemptsLeft()) {
            // CP-16: duas falhas indicam um problema que uma terceira tentativa não resolve.
            return false;
        }

        execution.markProcessing();
        repository.save(execution);

        try {
            GeneratedFile file =
                    generator.generate(
                            execution.getId(),
                            execution.getReportType(),
                            execution.getFormat(),
                            deserialize(execution.getParameters(), ExportParameters.class),
                            deserialize(execution.getOptions(), ExportOptions.class));
            complete(execution, file);
            return true;
        } catch (RuntimeException failure) {
            // ER-01: a exceção é tratada, não engolida — ela vira estado FAILED, motivo para o
            // usuário e log WARN (§28). ER-02 permite o catch amplo aqui porque este é o limite de
            // isolamento entre uma exportação e todas as outras (RNF-025).
            fail(execution, failure);
            return true;
        }
    }

    private void complete(ReportExecution execution, GeneratedFile file) {
        execution.markCompleted(
                file.storageKey(),
                file.fileName(),
                file.sizeBytes(),
                file.rowCount(),
                clock.now(),
                clock.now().plus(ExportServiceImpl.RETENTION));
        repository.save(execution);

        auditService.record(
                "REPORT_EXPORT_COMPLETED",
                ENTITY_TYPE,
                execution.getId(),
                Map.of("status", ExportStatus.PROCESSING.name()),
                Map.of("status", ExportStatus.COMPLETED.name(), "storageKey", file.storageKey()));

        events.publish(
                new ExportCompletedEvent(
                        execution.getId(),
                        execution.getRequestedBy(),
                        execution.getFormat().name(),
                        file.rowCount()));

        log.info(
                "exportação concluída execucao={} formato={} bytes={}",
                execution.getId(),
                execution.getFormat(),
                file.sizeBytes());
    }

    private void fail(ReportExecution execution, RuntimeException failure) {
        execution.markFailed(GENERIC_FAILURE);
        repository.save(execution);

        auditService.record(
                "REPORT_EXPORT_FAILED",
                ENTITY_TYPE,
                execution.getId(),
                Map.of("status", ExportStatus.PROCESSING.name()),
                Map.of("status", ExportStatus.FAILED.name(), "failureReason", GENERIC_FAILURE),
                Map.of("attemptCount", execution.getAttemptCount()));

        events.publish(
                new ExportFailedEvent(
                        execution.getId(),
                        execution.getRequestedBy(),
                        execution.getFormat().name(),
                        GENERIC_FAILURE));

        // §28: WARN, com a causa. A causa vai para o log do operador, não para a resposta.
        log.warn(
                "exportação falhou execucao={} tentativa={}",
                execution.getId(),
                execution.getAttemptCount(),
                failure);
    }

    /**
     * Expiração de uma exportação concluída (§22.4, SG-09).
     *
     * <p>A ordem importa: o binário sai <b>antes</b> de o registro perder a chave. Invertida, uma
     * falha entre os dois passos deixaria o objeto no storage sem nenhum registro que o aponte —
     * órfão invisível, o pior resultado possível para SG-09. Nesta ordem, uma falha deixa o
     * registro ainda {@code COMPLETED} e a próxima execução do job tenta de novo (BR-185).
     */
    @Transactional
    public boolean expire(java.util.UUID executionId) {
        ReportExecution execution = repository.findById(executionId).orElse(null);
        if (execution == null || execution.getStatus() != ExportStatus.COMPLETED) {
            return false;
        }

        storage.delete(execution.getStorageKey());
        execution.markExpired();
        repository.save(execution);

        auditService.recordSystemAction(
                "REPORT_EXPORT_EXPIRED",
                ENTITY_TYPE,
                execution.getId(),
                Map.of("status", ExportStatus.COMPLETED.name()),
                Map.of("status", ExportStatus.EXPIRED.name()),
                Map.of());
        return true;
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Parâmetros de exportação ilegíveis", e);
        }
    }
}
