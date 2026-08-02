package com.devtime.attachment;

import com.devtime.attachment.dto.AttachmentResponses.DownloadResponse;
import java.util.UUID;

/**
 * Download de anexo (spec §22.2).
 *
 * <p>CP-15 / OB-06: o binário <b>nunca</b> passa pela aplicação. O que é devolvido é uma URL
 * assinada de validade curta, e o cliente busca direto no storage. Além de banda e memória, a
 * consequência é de segurança — a aplicação nunca manipula o conteúdo depois do upload, o que reduz
 * a superfície para exploração via biblioteca de processamento de arquivo.
 */
public interface AttachmentDownloadService {

    /**
     * URL assinada, apenas com {@code scanStatus = CLEAN}.
     *
     * @param requestIp registrado na trilha; §18 audita <b>todo</b> download, porque é o momento em
     *     que conteúdo binário sai do sistema
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2703} — {@code 409} em
     *     {@code PENDING}/{@code FAILED}, {@code 403} em {@code INFECTED} (RN-803)
     */
    DownloadResponse download(UUID attachmentId, String requestIp);
}
