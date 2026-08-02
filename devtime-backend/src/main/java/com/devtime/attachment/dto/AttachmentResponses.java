package com.devtime.attachment.dto;

import com.devtime.attachment.domain.ScanStatus;
import com.devtime.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de saída da feature 015 (tickets.md §11, spec §23).
 *
 * <p><b>{@code storageKey} e {@code checksumSha256} nunca aparecem</b> (CP-07, CA-21). A {@code
 * storageKey} revelaria a estrutura do storage; o {@code checksum} permitiria verificar se um
 * arquivo específico existe no tenant sem tê-lo — canal de inferência análogo ao que a deduplicação
 * restrita ao tenant evita (§6.4).
 */
public final class AttachmentResponses {

    private AttachmentResponses() {}

    /**
     * Metadados de um anexo.
     *
     * @param fileName nome sanitizado (RN-804); é o nome com que o arquivo é baixado
     * @param originalFileName o que o usuário enviou, preservado como metadado. SG-13: a UI o
     *     renderiza como texto escapado — o servidor não o altera
     * @param scanStatus estado de §4.9. A UI explica o bloqueio a partir dele (CP-20)
     * @param uploadedBy quem enviou; {@code Usuário Removido} quando o membro saiu (RN-458)
     * @param canDownload RN-803 calculado no <b>servidor</b>. O cliente não reimplementa a regra
     *     (§23) — reimplementá-la criaria uma segunda definição de "pode baixar", e as duas
     *     divergiriam na primeira mudança
     * @param canDelete ownership de OWN-07 ou {@code ATTACHMENT_DELETE_ANY}, também do servidor
     */
    @Schema(name = "AttachmentResponse")
    public record AttachmentResponse(
            UUID id,
            UUID ticketId,
            UUID commentId,
            String fileName,
            String originalFileName,
            String contentType,
            long sizeBytes,
            ScanStatus scanStatus,
            UserSummary uploadedBy,
            Instant createdAt,
            boolean canDownload,
            boolean canDelete) {}

    /**
     * Anexos de um alvo.
     *
     * @param maxCount RN-806 para este tipo de alvo; permite à UI desabilitar o envio antes da
     *     tentativa, em vez de deixar o usuário selecionar um arquivo para receber {@code 422}
     */
    @Schema(name = "AttachmentListResponse")
    public record AttachmentListResponse(
            List<AttachmentResponse> attachments, int count, int maxCount) {}

    /**
     * Consumo de armazenamento do tenant (RN-801).
     *
     * @param percentage inteiro de 0 a 100; acima de 80 justifica aviso na UI (§29)
     */
    @Schema(name = "QuotaResponse")
    public record QuotaResponse(long usedBytes, long limitBytes, int percentage) {}

    /**
     * URL assinada de download (SG-08).
     *
     * <p>O binário <b>não</b> passa pela aplicação (CP-15, OB-06): o cliente busca direto no
     * storage. Além do desempenho, a consequência é de segurança — a aplicação nunca manipula o
     * conteúdo depois do upload, reduzindo a superfície para exploração via biblioteca de
     * processamento de arquivo.
     *
     * @param expiresAt validade curta; uma URL longeva é um link público com data marcada
     */
    @Schema(name = "AttachmentDownloadResponse")
    public record DownloadResponse(String url, Instant expiresAt) {}
}
