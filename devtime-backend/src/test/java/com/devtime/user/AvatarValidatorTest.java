package com.devtime.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SG-08 / RN-802: allowlist e assinatura binária do avatar (users.md §5.3).
 *
 * <p>O caso decisivo é o último: um conteúdo cuja assinatura é conhecida, mas <b>não</b> a do tipo
 * declarado. É ele que distingue verificação cruzada de mera allowlist.
 */
class AvatarValidatorTest {

    private final AvatarValidator validator = new AvatarValidator();

    private InputStream content(String hex) {
        byte[] header = HexFormat.of().parseHex(hex);
        byte[] padded = new byte[Math.max(header.length, 32)];
        System.arraycopy(header, 0, padded, 0, header.length);
        return new ByteArrayInputStream(padded);
    }

    @Test
    @DisplayName("PNG legítimo é aceito")
    void pngIsAccepted() {
        assertThatCode(
                        () ->
                                validator.validate(
                                        1024, "image/png", content("89504e470d0a1a0a0000000d")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JPEG legítimo é aceito")
    void jpegIsAccepted() {
        assertThatCode(
                        () ->
                                validator.validate(
                                        1024, "image/jpeg", content("ffd8ffe000104a464946")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("WebP exige RIFF seguido do rótulo WEBP")
    void webpRequiresFormatLabel() {
        assertThatCode(
                        () ->
                                validator.validate(
                                        1024, "image/webp", content("52494646aabbccdd57454250")))
                .doesNotThrowAnyException();
        // RIFF com rótulo WAVE: mesmo contêiner, formato diferente.
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        1024, "image/webp", content("52494646aabbccdd57415645")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("RN-802: PDF declarado como image/png é recusado — DEVTIME-2702")
    void mismatchedSignatureIsRejected() {
        assertThatThrownBy(() -> validator.validate(1024, "image/png", content("255044462d312e34")))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("SG-08: SVG não está na allowlist — DEVTIME-2702")
    void svgIsNotAllowed() {
        assertThatThrownBy(
                        () -> validator.validate(1024, "image/svg+xml", content("3c3f786d6c2076")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("RN-801: acima de 2 MB devolve DEVTIME-2701, antes de ler o conteúdo")
    void oversizeIsRejectedBeforeReading() {
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        AvatarValidator.MAX_SIZE_BYTES + 1,
                                        "image/png",
                                        content("89504e470d0a1a0a")))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ATTACHMENT_TOO_LARGE));
    }

    @Test
    @DisplayName("Arquivo vazio é recusado")
    void emptyFileIsRejected() {
        assertThatThrownBy(() -> validator.validate(0, "image/png", content("89504e47")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
