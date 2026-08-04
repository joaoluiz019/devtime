package com.devtime.report;

import com.devtime.audit.AuditActorNameResolver;
import com.devtime.report.domain.ExportStatus;
import com.devtime.report.domain.ReportExecution;
import com.devtime.report.dto.ExportResponses.ExportExecutionResponse;
import com.devtime.report.dto.ExportResponses.ExportProgress;
import com.devtime.report.dto.ExportResponses.ExportResponse;
import com.devtime.report.dto.ReportResponses.ReportUserRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ReportExecution} → DTO (§8.1 e §8.2 de reports.md).
 *
 * <p>Escrito à mão em vez de MapStruct porque quase nenhum campo é cópia direta: o progresso é
 * derivado, a URL assinada vem de fora do registro e a mensagem de §8.1 depende do modo. Um mapper
 * gerado com {@code ReportingPolicy.ERROR} exigiria uma expressão para cada um deles, e o resultado
 * seria menos legível que este.
 *
 * <p>ART-061: a entidade nunca sai daqui.
 */
@Component
@RequiredArgsConstructor
public class ExportMapper {

    /** §8.1, resposta do modo assíncrono. */
    private static final String QUEUED_MESSAGE =
            "A exportação está sendo processada. Você será notificado ao concluir.";

    private final AuditActorNameResolver actorNames;

    /** §8.1: resposta do modo síncrono, ou de uma solicitação idempotente já concluída. */
    public ExportResponse toExportResponse(ReportExecution execution, String downloadUrl) {
        return new ExportResponse(
                execution.getId(),
                execution.getStatus(),
                execution.getFormat(),
                execution.getFileName(),
                execution.getSizeBytes(),
                execution.getRowCount(),
                null,
                downloadUrl,
                null,
                execution.getExpiresAt(),
                execution.getCompletedAt(),
                null);
    }

    /** §8.1: {@code 202} com {@code pollUrl}. */
    public ExportResponse toQueuedResponse(ReportExecution execution, int estimatedRowCount) {
        return new ExportResponse(
                execution.getId(),
                ExportStatus.QUEUED,
                execution.getFormat(),
                null,
                null,
                null,
                estimatedRowCount,
                null,
                "/api/v1/reports/exports/" + execution.getId(),
                null,
                null,
                QUEUED_MESSAGE);
    }

    /** §8.2: acompanhamento e listagem. */
    public ExportExecutionResponse toExecutionResponse(ReportExecution execution) {
        return new ExportExecutionResponse(
                execution.getId(),
                execution.getStatus(),
                execution.getReportType(),
                execution.getFormat(),
                requesterRef(execution),
                // RN-707 / OB-08: os filtros voltam para o cliente, que é o que torna a exportação
                // reproduzível a partir da própria listagem.
                execution.getParameters(),
                progress(execution),
                execution.getRowCount(),
                execution.getFileName(),
                execution.getSizeBytes(),
                execution.getAttemptCount(),
                execution.getFailureReason(),
                execution.getCreatedAt(),
                execution.getCompletedAt(),
                execution.getExpiresAt());
    }

    /**
     * §8.2: progresso apenas durante {@code PROCESSING}.
     *
     * <p>Nos demais estados o campo vem nulo em vez de zerado ou completo: um progresso de 100% ao
     * lado de {@code FAILED} seria contraditório, e de 0% ao lado de {@code QUEUED} sugeriria que o
     * worker já assumiu.
     */
    private ExportProgress progress(ReportExecution execution) {
        if (execution.getStatus() != ExportStatus.PROCESSING) {
            return null;
        }
        int processed = execution.getProcessedRows() == null ? 0 : execution.getProcessedRows();
        int total = execution.getRowCount() == null ? 0 : execution.getRowCount();
        BigDecimal percentage =
                total == 0
                        ? BigDecimal.ZERO.setScale(2)
                        : BigDecimal.valueOf(processed)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new ExportProgress(processed, total, percentage);
    }

    /**
     * §19.1: o solicitante aparece com nome, nunca só com o identificador.
     *
     * <p>Resolvido por {@code AuditActorNameResolver} e não por {@code UserService}: quem exportou
     * pode já ter deixado o tenant, e esta é a porta que devolve o nome nesse caso sem lançar —
     * identificadores desconhecidos simplesmente ficam ausentes do mapa. Omitir a linha inteira da
     * listagem apagaria a trilha de quem exportou, que é o oposto do que §18 exige.
     */
    private ReportUserRef requesterRef(ReportExecution execution) {
        UUID requestedBy = execution.getRequestedBy();
        return new ReportUserRef(
                requestedBy, actorNames.namesOf(List.of(requestedBy)).get(requestedBy));
    }
}
