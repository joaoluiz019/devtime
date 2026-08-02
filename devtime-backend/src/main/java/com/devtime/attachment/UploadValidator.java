package com.devtime.attachment;

import com.devtime.attachment.domain.AttachmentExceptions;
import com.devtime.attachment.domain.UploadContent;
import com.devtime.shared.config.DevTimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Passo 5 de §6.1 — tamanho do arquivo (RN-801).
 *
 * <p><b>A posição deste passo na ordem é normativa</b> (BR-062, CE-02, CA-03): o tamanho é
 * verificado <b>antes</b> de qualquer leitura de conteúdo. §6.1 explica o porquê — um arquivo de
 * 500 MB deve ser rejeitado antes de qualquer leitura; ler os primeiros bytes de um upload que será
 * descartado por tamanho desperdiça banda e abre caminho para exaustão de recursos (SG-11).
 *
 * <p>É por isso que esta classe recebe {@link UploadContent} e usa apenas {@code sizeBytes()}, sem
 * jamais chamar {@code openStream()}.
 */
@Component
@RequiredArgsConstructor
public class UploadValidator {

    private final DevTimeProperties properties;

    /**
     * CX-01: exatamente 10 MB é aceito; 10 MB + 1 byte é rejeitado. CX-02: 0 byte é rejeitado —
     * nenhuma assinatura corresponde, e o {@code CHECK} de {@code V023} o recusaria de todo modo.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2701} / {@code 413}
     */
    public void assertSizeWithinLimit(UploadContent content) {
        long max = properties.attachment().maxFileSizeBytes();
        long size = content.sizeBytes();
        if (size <= 0 || size > max) { // RN-801
            throw AttachmentExceptions.fileTooLarge(size, max);
        }
    }
}
