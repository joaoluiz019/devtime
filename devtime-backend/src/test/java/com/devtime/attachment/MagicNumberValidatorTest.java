package com.devtime.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.attachment.domain.AllowedFileType;
import com.devtime.shared.error.BusinessRuleException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Suíte de assinaturas (T-015-05, T-015-25; RN-802, INV-ATT-03).
 *
 * <p><b>Os casos negativos são o ponto</b> (T-015-05): cada tipo é declarado com a assinatura de
 * outro. Uma implementação que apenas verifica se a assinatura está na allowlist — sem cruzá-la com
 * o {@code contentType} declarado — passa em todos os casos positivos e falha em todos os
 * negativos. É por isso que esta suíte foi escrita antes do validador.
 *
 * <p>DoD-03 exige cobertura ≥ 95% nesta classe.
 */
class MagicNumberValidatorTest {

    private final MagicNumberValidator validator = new MagicNumberValidator();

    /** Um caso positivo por tipo da allowlist — CA-05, os 9 tipos de §6.2. */
    static Stream<Arguments> allowedTypes() {
        return Stream.of(
                Arguments.of("image/png", AttachmentFixtures.png()),
                Arguments.of("image/jpeg", AttachmentFixtures.jpeg()),
                Arguments.of("image/gif", AttachmentFixtures.gif()),
                Arguments.of("image/webp", AttachmentFixtures.webp()),
                Arguments.of("application/pdf", AttachmentFixtures.pdf()),
                Arguments.of("text/plain", AttachmentFixtures.plainText()),
                Arguments.of("text/csv", AttachmentFixtures.csv()),
                Arguments.of("application/zip", AttachmentFixtures.zip()),
                Arguments.of(
                        AllowedFileType.DOCX.contentType(),
                        AttachmentFixtures.office("wordprocessingml.document")),
                Arguments.of(
                        AllowedFileType.XLSX.contentType(),
                        AttachmentFixtures.office("spreadsheetml.sheet")),
                Arguments.of(
                        AllowedFileType.PPTX.contentType(),
                        AttachmentFixtures.office("presentationml.presentation")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allowedTypes")
    @DisplayName("RN-802/CA-05: cada tipo da allowlist é aceito com a assinatura correta")
    void shouldAcceptEachAllowedTypeWithMatchingSignature(String contentType, byte[] content) {
        assertThatCode(
                        () ->
                                validator.assertDeclaredTypeMatchesContent(
                                        AttachmentFixtures.upload("arquivo", contentType, content)))
                .doesNotThrowAnyException();
    }

    /**
     * Casos negativos cruzados — cada tipo declarado com a assinatura de outro (T-015-25).
     *
     * <p>Inclui CX-03 (executável como PDF) e CX-04 (PDF como PNG).
     */
    static Stream<Arguments> crossedSignatures() {
        return Stream.of(
                // CX-03: executável renomeado para cada extensão da allowlist.
                Arguments.of("application/pdf", AttachmentFixtures.windowsExecutable()),
                Arguments.of("image/png", AttachmentFixtures.windowsExecutable()),
                Arguments.of("image/jpeg", AttachmentFixtures.windowsExecutable()),
                Arguments.of("image/gif", AttachmentFixtures.windowsExecutable()),
                Arguments.of("image/webp", AttachmentFixtures.windowsExecutable()),
                Arguments.of("application/zip", AttachmentFixtures.windowsExecutable()),
                Arguments.of("text/plain", AttachmentFixtures.windowsExecutable()),
                Arguments.of("text/csv", AttachmentFixtures.windowsExecutable()),
                // CX-04: PDF declarado como PNG, e o inverso.
                Arguments.of("image/png", AttachmentFixtures.pdf()),
                Arguments.of("application/pdf", AttachmentFixtures.png()),
                // JPEG declarado como GIF.
                Arguments.of("image/gif", AttachmentFixtures.jpeg()),
                // RIFF sem WEBP no deslocamento 8 seria aceito por uma verificação incompleta.
                Arguments.of("image/webp", AttachmentFixtures.zip()),
                // CX-05: ZIP renomeado para .docx — mesma assinatura, manifesto ausente.
                Arguments.of(
                        AllowedFileType.DOCX.contentType(),
                        AttachmentFixtures.zipWithoutOfficeManifest()),
                // Office com o manifesto do formato errado.
                Arguments.of(
                        AllowedFileType.XLSX.contentType(),
                        AttachmentFixtures.office("wordprocessingml.document")),
                // CX-07: texto com byte nulo.
                Arguments.of("text/plain", AttachmentFixtures.textWithNullByte()),
                Arguments.of("text/csv", AttachmentFixtures.textWithNullByte()),
                // CX-02: arquivo de 0 byte não corresponde a nenhuma assinatura.
                Arguments.of("image/png", AttachmentFixtures.empty()),
                Arguments.of("text/plain", AttachmentFixtures.empty()));
    }

    @ParameterizedTest(name = "declarado {0} com assinatura de outro tipo")
    @MethodSource("crossedSignatures")
    @DisplayName("RN-802/CA-04: assinatura divergente do tipo declarado é rejeitada com 415")
    void shouldRejectMismatchedSignature(String declaredContentType, byte[] content) {
        assertThatThrownBy(
                        () ->
                                validator.assertDeclaredTypeMatchesContent(
                                        AttachmentFixtures.upload(
                                                "arquivo", declaredContentType, content)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2702");
    }

    @Test
    @DisplayName("RN-802/RS-02: tipo fora da allowlist é rejeitado no passo 7, com 415")
    void shouldRejectTypeOutsideAllowlist() {
        assertThatThrownBy(
                        () ->
                                validator.assertDeclaredTypeMatchesContent(
                                        AttachmentFixtures.upload(
                                                "programa.exe",
                                                "application/x-msdownload",
                                                AttachmentFixtures.windowsExecutable())))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2702");
    }

    @Test
    @DisplayName("RN-802: contentType ausente ou em branco é rejeitado")
    void shouldRejectMissingContentType() {
        assertThatThrownBy(
                        () ->
                                validator.assertDeclaredTypeMatchesContent(
                                        AttachmentFixtures.upload(
                                                "arquivo", null, AttachmentFixtures.png())))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(
                        () ->
                                validator.assertDeclaredTypeMatchesContent(
                                        AttachmentFixtures.upload(
                                                "arquivo", "   ", AttachmentFixtures.png())))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("RN-802: parâmetro de charset e caixa não alteram o tipo declarado")
    void shouldNormalizeContentTypeParameters() {
        assertThatCode(
                        () ->
                                validator.assertDeclaredTypeMatchesContent(
                                        AttachmentFixtures.upload(
                                                "notas.txt",
                                                "TEXT/PLAIN; charset=utf-8",
                                                AttachmentFixtures.plainText())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("§6.2: texto com acentuação em UTF-8 é aceito; sequência inválida é rejeitada")
    void shouldValidateUtf8Decoding() {
        assertThat(validator.isPlausibleText(AttachmentFixtures.plainText())).isTrue();
        // 0xC3 inicia um caractere de dois bytes; 0x28 não é continuação válida.
        assertThat(validator.isPlausibleText(new byte[] {(byte) 0xC3, 0x28})).isFalse();
    }

    @Test
    @DisplayName("§6.2: caractere multibyte cortado no limite da janela não reprova o arquivo")
    void shouldTolerateTruncatedMultibyteAtWindowBoundary() {
        // Apenas o primeiro byte de "ç": entrada incompleta, não malformada.
        assertThat(validator.isPlausibleText(new byte[] {0x61, (byte) 0xC3})).isTrue();
    }

    @Test
    @DisplayName("CX-07: tabulação e quebra de linha são aceitas; outros controles não")
    void shouldAcceptOnlyExpectedControlCharacters() {
        assertThat(validator.isPlausibleText("a\tb\r\nc\f".getBytes())).isTrue();
        // 0x07 (BEL) é controle atípico de texto.
        assertThat(validator.isPlausibleText(new byte[] {0x61, 0x07})).isFalse();
    }

    @Test
    @DisplayName("CG-06: conteúdo ilegível falha fechado, com DEVTIME-2702")
    void unreadableContentMustFailClosed() {
        com.devtime.attachment.domain.UploadContent unreadable =
                new com.devtime.attachment.domain.UploadContent() {
                    @Override
                    public String originalFileName() {
                        return "quebrado.png";
                    }

                    @Override
                    public String declaredContentType() {
                        return "image/png";
                    }

                    @Override
                    public long sizeBytes() {
                        return 128;
                    }

                    @Override
                    public java.io.InputStream openStream() throws java.io.IOException {
                        throw new java.io.IOException("fluxo interrompido");
                    }
                };

        assertThatThrownBy(() -> validator.assertDeclaredTypeMatchesContent(unreadable))
                .as("tratar 'não consegui verificar' como 'está certo' é o inverso de AV-02")
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2702");
    }

    @Test
    @DisplayName("CX-05: contêiner Office corrompido é rejeitado, e não aceito por omissão")
    void corruptedOfficeContainerMustBeRejected() {
        // Assinatura de ZIP seguida de lixo: o contêiner abre e falha na primeira entrada.
        byte[] corrupted = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00, 0x11, 0x22, 0x33, 0x44};

        assertThatThrownBy(
                        () ->
                                validator.assertDeclaredTypeMatchesContent(
                                        AttachmentFixtures.upload(
                                                "documento.docx",
                                                AllowedFileType.DOCX.contentType(),
                                                corrupted)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2702");
    }

    @Test
    @DisplayName("§20: apenas os primeiros 8 KB são lidos na verificação de assinatura")
    void shouldReadOnlyHeaderWindow() {
        // Um PNG de 1 MB é validado pela assinatura; se o validador lesse o arquivo inteiro, a
        // leitura seria de 1 MB e não dos 8 KB documentados.
        byte[] large = AttachmentFixtures.pngOfSize(1_048_576);
        CountingUploadContent counting =
                new CountingUploadContent(
                        AttachmentFixtures.upload("grande.png", "image/png", large));

        validator.assertDeclaredTypeMatchesContent(counting);

        assertThat(counting.bytesRead())
                .as("§20: leitura limitada à janela de assinatura")
                .isLessThanOrEqualTo(MagicNumberValidator.HEADER_SIZE);
    }

    /** Conta os bytes efetivamente lidos, para provar a janela de leitura de §20. */
    private static final class CountingUploadContent
            implements com.devtime.attachment.domain.UploadContent {

        private final com.devtime.attachment.domain.UploadContent delegate;
        private long bytesRead;

        private CountingUploadContent(com.devtime.attachment.domain.UploadContent delegate) {
            this.delegate = delegate;
        }

        long bytesRead() {
            return bytesRead;
        }

        @Override
        public String originalFileName() {
            return delegate.originalFileName();
        }

        @Override
        public String declaredContentType() {
            return delegate.declaredContentType();
        }

        @Override
        public long sizeBytes() {
            return delegate.sizeBytes();
        }

        @Override
        public java.io.InputStream openStream() throws java.io.IOException {
            return new java.io.FilterInputStream(delegate.openStream()) {
                @Override
                public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
                    int read = super.read(buffer, offset, length);
                    if (read > 0) {
                        bytesRead += read;
                    }
                    return read;
                }
            };
        }
    }
}
