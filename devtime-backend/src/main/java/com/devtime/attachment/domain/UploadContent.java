package com.devtime.attachment.domain;

import java.io.IOException;
import java.io.InputStream;

/**
 * Conteúdo enviado, na forma que a camada de serviço consegue consumir.
 *
 * <p>BR-069: o serviço nunca conhece tipos de {@code org.springframework.web}. O controller adapta
 * o {@code MultipartFile} a esta interface, e é a única fronteira em que o tipo web aparece.
 *
 * <p>{@link #openStream()} pode ser chamado <b>mais de uma vez</b>, e é chamado três vezes no fluxo
 * de §6.1: assinatura (primeiros bytes), checksum e gravação. Ler o conteúdo uma vez para uma cópia
 * em memória reaproveitada nas três seria mais simples e violaria CP-14 — com uploads concorrentes
 * de 10 MB, "mais simples" vira {@code OutOfMemory} (R-07).
 */
public interface UploadContent {

    /** Nome enviado pelo cliente, antes da sanitização de RN-804. */
    String originalFileName();

    /** RN-802, passo 7: o que o cliente <b>declara</b>. O passo 8 verifica o que o arquivo é. */
    String declaredContentType();

    /** Tamanho conhecido antes de qualquer leitura de conteúdo (CA-03). */
    long sizeBytes();

    InputStream openStream() throws IOException;
}
