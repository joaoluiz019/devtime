package com.devtime.report.dto;

import com.devtime.report.domain.ExportFormat;
import com.devtime.report.domain.ExportStatus;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportResponses.ReportUserRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Respostas de exportação (§8 de reports.md, §23 de specs/012). */
public final class ExportResponses {

    private ExportResponses() {}

    /**
     * Resposta de {@code POST /reports/exports} nos dois modos (§8.1).
     *
     * <p>Um único tipo para {@code 200} e {@code 202}, com os campos do outro modo nulos, porque é
     * assim que §8.1 os documenta — e porque o cliente distingue pelo {@code status}, não pelo
     * formato do corpo.
     *
     * @param downloadUrl presente apenas no modo síncrono; assinada, com 15 minutos (RN-712)
     * @param pollUrl presente apenas no modo assíncrono (FA-10)
     * @param estimatedRowCount contagem que decidiu o modo; é a mesma que vira {@code rowCount}
     */
    @Schema(name = "ExportResponse")
    public record ExportResponse(
            UUID id,
            ExportStatus status,
            ExportFormat format,
            String fileName,
            Long sizeBytes,
            Integer rowCount,
            Integer estimatedRowCount,
            String downloadUrl,
            String pollUrl,
            Instant expiresAt,
            Instant generatedAt,
            String message) {}

    /** Progresso durante {@code PROCESSING} (§8.2). */
    @Schema(name = "ExportProgress")
    public record ExportProgress(int processedRows, int totalRows, BigDecimal percentage) {}

    /**
     * Acompanhamento e listagem (§8.2).
     *
     * @param parameters os filtros aplicados, devolvidos para reprodutibilidade (RN-707, OB-08)
     * @param failureReason preenchido em {@code FAILED}; nunca carrega SQL nem nome de tabela
     *     (BR-092)
     */
    @Schema(name = "ExportExecutionResponse")
    public record ExportExecutionResponse(
            UUID id,
            ExportStatus status,
            ReportType reportType,
            ExportFormat format,
            ReportUserRef requestedBy,
            String parameters,
            ExportProgress progress,
            Integer rowCount,
            String fileName,
            Long sizeBytes,
            int attemptCount,
            String failureReason,
            Instant createdAt,
            Instant completedAt,
            Instant expiresAt) {}
}
