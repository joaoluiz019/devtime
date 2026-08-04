package com.devtime.report;

import com.devtime.audit.AuditService;
import com.devtime.report.ExportGenerator.GeneratedFile;
import com.devtime.report.domain.ExportStatus;
import com.devtime.report.domain.ReportExceptions;
import com.devtime.report.domain.ReportExecution;
import com.devtime.report.dto.ExportRequests.ExportParameters;
import com.devtime.report.dto.ExportRequests.ExportRequest;
import com.devtime.report.dto.ExportResponses.ExportExecutionResponse;
import com.devtime.report.dto.ExportResponses.ExportResponse;
import com.devtime.report.event.ExportEvents.ExportRequestedEvent;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.error.RateLimitExceededException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.pagination.PageRequestFactory;
import com.devtime.shared.pagination.PageResponse;
import com.devtime.shared.persistence.UuidGenerator;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitação, acompanhamento, download e cancelamento de exportações (§8 de reports.md).
 *
 * <p><b>O limiar de 5.000 linhas é "acima de", não "a partir de"</b> (RN-706, CX-10, CX-11):
 * exatamente 5.000 é síncrono; 5.001 é assíncrono. A contagem é do resultado <b>filtrado</b> e
 * acontece <b>antes</b> de qualquer linha ser materializada (CP-13) — contar carregando anularia a
 * proteção no exato passo que existe para evitá-la.
 *
 * <p>§28 e CP-18: nenhum log carrega os filtros nem conteúdo de linha. O que sai é o identificador
 * da execução, o formato, a contagem e o modo.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ExportServiceImpl implements ExportService {

    /** RN-706, RS-02. */
    public static final int SYNC_ROW_THRESHOLD = 5_000;

    /** §19.1 e RS-04: o arquivo é de uso imediato. */
    public static final Duration RETENTION = Duration.ofDays(7);

    /** §8.1: teto de 20 exportações por hora por tenant. */
    private static final int HOURLY_LIMIT = 20;

    private static final String ENTITY_TYPE = "ReportExecution";

    private final ReportExecutionRepository repository;
    private final ExportGenerator generator;
    private final ExportRowCounter rowCounter;
    private final ExportMapper mapper;
    private final SignedUrlProvider signedUrlProvider;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final PageRequestFactory pageRequestFactory;
    private final ObjectMapper objectMapper;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'REPORT_EXPORT')")
    public ExportResponse request(ExportRequest request, String idempotencyKey) {
        UUID requesterId = tenantContext.requireUserId();

        // ART-074 / CE-R-12: um duplo clique não gera dois arquivos nem consome duas cotas.
        Optional<ReportExecution> existing = existingFor(requesterId, idempotencyKey);
        if (existing.isPresent()) {
            return mapper.toExportResponse(existing.get(), downloadUrlOrNull(existing.get()));
        }

        assertWithinHourlyLimit();

        ExportParameters parameters = request.parametersOrEmpty();
        // Passo 10 de §6.2 — a contagem também aplica escopo e intervalo, e é por isso que ela vem
        // depois da permissão e antes de qualquer geração.
        long rowCount = rowCounter.count(request.reportType(), parameters);
        boolean asynchronous = rowCount > SYNC_ROW_THRESHOLD;

        ReportExecution execution =
                persistRequested(request, idempotencyKey, requesterId, rowCount, asynchronous);

        auditService.record(
                "REPORT_EXPORT_REQUESTED",
                ENTITY_TYPE,
                execution.getId(),
                Map.of(),
                Map.of(
                        "reportType", request.reportType().name(),
                        "format", request.format().name(),
                        "rowCount", rowCount),
                // §18: os filtros aplicados vão para a trilha — não para o log (§28).
                Map.of("parameters", serialize(parameters)));

        if (asynchronous) {
            events.publish(new ExportRequestedEvent(execution.getId(), requesterId, clock.now()));
            log.info(
                    "exportação solicitada execucao={} formato={} linhas={} modo=async",
                    execution.getId(),
                    request.format(),
                    rowCount);
            return mapper.toQueuedResponse(execution, (int) rowCount);
        }

        GeneratedFile file = generateNow(execution, request);
        log.info(
                "exportação solicitada execucao={} formato={} linhas={} modo=sync",
                execution.getId(),
                request.format(),
                file.rowCount());
        return mapper.toExportResponse(execution, downloadUrlOrNull(execution));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'REPORT_EXPORT')")
    public PageResponse<ExportExecutionResponse> list(Pageable pageable) {
        Pageable validated = pageRequestFactory.validate(pageable); // RN-012
        return PageResponse.of(
                repository.findByRequester(tenantContext.requireUserId(), validated),
                mapper::toExecutionResponse);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'REPORT_EXPORT')")
    public ExportExecutionResponse get(UUID executionId) {
        return mapper.toExecutionResponse(require(executionId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'REPORT_EXPORT')")
    public String downloadUrl(UUID executionId) {
        ReportExecution execution = require(executionId);
        assertDownloadable(execution);

        // §18: o download é auditado. É a única resposta possível para "quem teve acesso a este
        // relatório consolidado" quando a pergunta aparece meses depois.
        auditService.record(
                "REPORT_DOWNLOADED", ENTITY_TYPE, execution.getId(), Map.of(), Map.of());
        log.info(
                "download de exportação execucao={} usuario={}",
                execution.getId(),
                tenantContext.currentUserId().orElse(null));

        // FA-13: nova assinatura sobre o mesmo objeto; o arquivo não é regerado.
        return signedUrlProvider.urlFor(execution.getStorageKey(), execution.getFileName());
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'REPORT_EXPORT')")
    public void cancel(UUID executionId) {
        ReportExecution execution = require(executionId);
        if (!execution.getStatus().isCancellable()) {
            // CP-15: cancelar em PROCESSING exigiria interromper o worker no meio da geração.
            throw ReportExceptions.exportNotReady(execution.getStatus());
        }
        repository.softDelete(execution.getId(), clock.now(), tenantContext.requireUserId());
    }

    /**
     * §11: a execução nasce {@code QUEUED} ou já {@code COMPLETED}; nunca em um terceiro estado.
     */
    private ReportExecution persistRequested(
            ExportRequest request,
            String idempotencyKey,
            UUID requesterId,
            long rowCount,
            boolean asynchronous) {
        ReportExecution execution = new ReportExecution();
        execution.setId(UuidGenerator.newId());
        execution.setReportType(request.reportType());
        execution.setFormat(request.format());
        execution.setParameters(serialize(request.parametersOrEmpty()));
        execution.setOptions(serialize(request.optionsOrDefault()));
        execution.setIdempotencyKey(idempotencyKey);
        execution.setRequestedBy(requesterId);
        execution.setStatus(ExportStatus.QUEUED);
        execution.setRowCount((int) Math.min(rowCount, Integer.MAX_VALUE));
        execution.setAttemptCount((short) 0);
        if (!asynchronous) {
            // O modo síncrono já gastou a tentativa: se a geração falhar, o registro fica FAILED
            // com uma tentativa consumida, e o worker pode reprocessá-lo uma única vez (CP-16).
            execution.markProcessing();
        }
        return repository.save(execution);
    }

    /** FA-09: até 5.000 linhas o arquivo é gerado na própria requisição. */
    private GeneratedFile generateNow(ReportExecution execution, ExportRequest request) {
        GeneratedFile file =
                generator.generate(
                        execution.getId(),
                        request.reportType(),
                        request.format(),
                        request.parametersOrEmpty(),
                        request.optionsOrDefault());
        execution.markCompleted(
                file.storageKey(),
                file.fileName(),
                file.sizeBytes(),
                file.rowCount(),
                clock.now(),
                clock.now().plus(RETENTION));
        auditService.record(
                "REPORT_EXPORT_COMPLETED",
                ENTITY_TYPE,
                execution.getId(),
                Map.of("status", ExportStatus.PROCESSING.name()),
                Map.of("status", ExportStatus.COMPLETED.name(), "storageKey", file.storageKey()));
        return file;
    }

    private Optional<ReportExecution> existingFor(UUID requesterId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKey(requesterId, idempotencyKey);
    }

    /** §8.1: {@code 429} acima de 20 por hora no tenant. */
    private void assertWithinHourlyLimit() {
        long recent = repository.countSince(clock.now().minus(Duration.ofHours(1)));
        if (recent >= HOURLY_LIMIT) {
            throw RateLimitExceededException.of("report-export", Duration.ofHours(1));
        }
    }

    /**
     * SG-04: uma exportação de terceiro é indistinguível de inexistente.
     *
     * <p>{@code 404} e não {@code 403}, pela mesma razão de ART-024 entre tenants: informar que ela
     * existe permitiria enumerar as exportações dos colegas pelo identificador.
     */
    private ReportExecution require(UUID executionId) {
        return repository
                .findByIdAndRequester(executionId, tenantContext.requireUserId())
                .orElseThrow(() -> EntityNotFoundException.of(ReportExecution.class, executionId));
    }

    /** §8.3: os três estados que impedem o download, cada um com o seu código. */
    private void assertDownloadable(ReportExecution execution) {
        switch (execution.getStatus()) {
            case COMPLETED -> {
                // Nada a fazer: é o único estado em que o arquivo existe (INV-RPT-05).
            }
            case EXPIRED -> throw ReportExceptions.exportExpired();
            case FAILED -> throw ReportExceptions.exportFailed(execution.getFailureReason());
            default -> throw ReportExceptions.exportNotReady(execution.getStatus());
        }
    }

    private String downloadUrlOrNull(ReportExecution execution) {
        return execution.getStatus() == ExportStatus.COMPLETED
                ? signedUrlProvider.urlFor(execution.getStorageKey(), execution.getFileName())
                : null;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar os parâmetros da exportação", e);
        }
    }
}
