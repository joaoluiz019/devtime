package com.devtime.attachment;

import com.devtime.attachment.domain.UploadContent;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * Adapta {@code MultipartFile} a {@link UploadContent}.
 *
 * <p>É a <b>única</b> classe da feature que conhece um tipo de {@code org.springframework.web}, e
 * por isso vive na fronteira: BR-069 proíbe o serviço de conhecer tipos HTTP, e {@code
 * MultipartFile} é um deles. A adaptação ocorre no controller (CE-G-07).
 *
 * <p>{@link #openStream()} delega a {@code MultipartFile#getInputStream()}, que devolve um fluxo
 * novo a cada chamada — o que é exatamente o contrato exigido por {@link UploadContent}, já que o
 * conteúdo é percorrido três vezes no fluxo de §6.1 sem nunca ser retido em memória (CP-14).
 */
record MultipartUploadContent(MultipartFile file) implements UploadContent {

    @Override
    public String originalFileName() {
        return file.getOriginalFilename();
    }

    @Override
    public String declaredContentType() {
        return file.getContentType();
    }

    @Override
    public long sizeBytes() {
        // Conhecido pelo cabeçalho da parte, sem leitura de conteúdo — é o que permite ao passo 5
        // de §6.1 preceder os passos 7 e 8 (CE-02, CA-03).
        return file.getSize();
    }

    @Override
    public InputStream openStream() throws IOException {
        return file.getInputStream();
    }
}
