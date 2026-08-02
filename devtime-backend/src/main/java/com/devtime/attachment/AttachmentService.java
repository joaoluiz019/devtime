package com.devtime.attachment;

import com.devtime.attachment.domain.UploadContent;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentListResponse;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentResponse;
import java.util.UUID;

/**
 * Interface pública da feature 015 (spec §22.2).
 *
 * <p><b>Não existe método de atualização</b> (CP-13, RN-011). Todos os campos relevantes são
 * imutáveis, e {@code scanStatus} é alterado apenas pelo verificador. A ausência é a implementação
 * da regra: alterar o {@code contentType} depois da verificação permitiria burlar a validação de
 * assinatura, que é a defesa central de OB-01.
 */
public interface AttachmentService {

    /** Anexos do ticket, com {@code maxCount} de RN-806. */
    AttachmentListResponse listByTicket(UUID ticketId);

    /** Anexos do comentário. */
    AttachmentListResponse listByComment(UUID commentId);

    /**
     * Envia um anexo, na ordem <b>exata</b> de §6.1 (BR-062).
     *
     * <p>Exatamente um dos dois identificadores é informado (INV-ATT-01).
     *
     * @param uploadedFromIp IP de origem, exigido pela trilha de {@code ATTACHMENT_SCAN_INFECTED}
     *     (§18) — é o único registro de uma tentativa de introduzir arquivo malicioso
     * @return o anexo criado, em {@code PENDING}; o <b>download continua bloqueado</b> (RN-803)
     */
    AttachmentResponse upload(
            UUID ticketId, UUID commentId, UploadContent content, String uploadedFromIp);

    /**
     * Exclusão lógica (RN-003), removendo o binário se este for o último referenciador (RN-805).
     *
     * <p>OWN-07: o autor exclui a qualquer momento — diferente de comentários, <b>não</b> há janela
     * temporal. Quem possui {@code ATTACHMENT_DELETE_ANY} modera.
     */
    void delete(UUID attachmentId);
}
