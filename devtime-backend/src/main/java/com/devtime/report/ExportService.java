package com.devtime.report;

import com.devtime.report.dto.ExportRequests.ExportRequest;
import com.devtime.report.dto.ExportResponses.ExportExecutionResponse;
import com.devtime.report.dto.ExportResponses.ExportResponse;
import com.devtime.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Ciclo de vida da exportação (§8 de reports.md, §22.2 de specs/012).
 *
 * <p>Interface interna à feature: {@code 012} é folha no grafo e não publica nada (§22.2).
 *
 * <p><b>Toda exportação registra uma {@code ReportExecution}</b>, nos dois modos (RN-707, CP-10,
 * INV-RPT-05). É o que responde "quem exportou o quê, com quais filtros" — e §18 registra que sem
 * os filtros a resposta seria inútil em investigação.
 */
public interface ExportService {

    /**
     * §8.1: solicita a exportação e decide entre síncrono e assíncrono no limiar de 5.000 linhas.
     *
     * @param idempotencyKey header {@code Idempotency-Key} (ART-074, CE-R-12); a mesma chave do
     *     mesmo solicitante devolve a <b>mesma</b> exportação, sem gerar um segundo arquivo
     */
    ExportResponse request(ExportRequest request, String idempotencyKey);

    /** §8: exportações do solicitante, paginadas. SG-04 restringe a listagem a ele mesmo. */
    PageResponse<ExportExecutionResponse> list(Pageable pageable);

    /** §8.2: acompanhamento, com progresso durante {@code PROCESSING}. */
    ExportExecutionResponse get(UUID executionId);

    /**
     * §8.3: URL assinada de 15 minutos.
     *
     * <p>FA-13: uma nova solicitação gera nova URL <b>sem regerar o arquivo</b>. Todo download é
     * auditado (§18): é a operação em que dado do tenant sai do sistema em forma de arquivo.
     */
    String downloadUrl(UUID executionId);

    /** §11.1 / FA-15: permitido apenas em {@code QUEUED}; em {@code PROCESSING} é recusado. */
    void cancel(UUID executionId);
}
