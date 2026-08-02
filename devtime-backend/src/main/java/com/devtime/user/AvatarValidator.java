package com.devtime.user;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validação do avatar (users.md §5.3, RN-801/RN-802).
 *
 * <p>Aplica a mesma defesa em duas camadas de {@code 015-attachments}, na mesma ordem: o tamanho é
 * verificado <b>antes</b> de qualquer leitura de conteúdo (CP-04), o tipo declarado é confrontado
 * com a allowlist e, por fim, a assinatura binária é confrontada com o tipo declarado. A
 * verificação cruzada é o que importa: perguntar "esta assinatura é conhecida?" aceitaria um PDF
 * declarado como {@code image/png}.
 *
 * <p>É uma classe própria, e não a de {@code attachment}: AR-02 proíbe alcançar componentes
 * internos de outra feature, e as regras não coincidem — o avatar tem teto de 2 MB e três tipos,
 * contra 10 MB e nove tipos do anexo.
 */
@Component
public class AvatarValidator {

    /** users.md §5.3. */
    static final long MAX_SIZE_BYTES = 2L * 1024 * 1024;

    static final String PNG = "image/png";
    static final String JPEG = "image/jpeg";
    static final String WEBP = "image/webp";

    static final Set<String> ALLOWED_TYPES = Set.of(PNG, JPEG, WEBP);

    private static final int HEADER_SIZE = 16;

    private static final Map<String, List<String>> SIGNATURES =
            Map.of(
                    PNG, List.of("89504e470d0a1a0a"),
                    // JFIF, EXIF e SPIFF compartilham o marcador SOI seguido de APPn.
                    JPEG, List.of("ffd8ff"),
                    // RIFF....WEBP: os 4 bytes de tamanho ficam entre os dois marcadores.
                    WEBP, List.of("52494646"));

    /**
     * @throws BusinessRuleException {@code DEVTIME-2701} acima de 2 MB, {@code DEVTIME-2702} para
     *     tipo fora da allowlist ou assinatura incompatível com o tipo declarado
     */
    public void validate(long sizeBytes, String declaredContentType, InputStream content) {
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw tooLarge(sizeBytes);
        }
        String contentType = declaredContentType == null ? "" : declaredContentType.toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw typeNotAllowed(contentType);
        }
        if (!matchesSignature(contentType, readHeader(content))) {
            throw typeNotAllowed(contentType);
        }
    }

    private boolean matchesSignature(String contentType, String headerHex) {
        boolean prefixMatches =
                SIGNATURES.get(contentType).stream().anyMatch(headerHex::startsWith);
        if (!prefixMatches) {
            return false;
        }
        if (!WEBP.equals(contentType)) {
            return true;
        }
        // O contêiner RIFF hospeda também WAV e AVI: sem verificar o rótulo de formato, um áudio
        // renomeado passaria pela mesma assinatura de quatro bytes.
        return headerHex.length() >= 24 && headerHex.startsWith("57454250", 16);
    }

    private String readHeader(InputStream content) {
        try {
            byte[] header = content.readNBytes(HEADER_SIZE);
            return HexFormat.of().formatHex(header);
        } catch (IOException unreadable) {
            throw typeNotAllowed("desconhecido");
        }
    }

    private BusinessRuleException tooLarge(long sizeBytes) {
        return new AvatarTooLargeException(sizeBytes);
    }

    private BusinessRuleException typeNotAllowed(String contentType) {
        return new AvatarTypeNotAllowedException(contentType);
    }

    /** {@code DEVTIME-2701} / 413. */
    public static final class AvatarTooLargeException extends BusinessRuleException {
        private AvatarTooLargeException(long sizeBytes) {
            super(
                    ErrorCode.ATTACHMENT_TOO_LARGE,
                    Map.of("sizeBytes", sizeBytes, "maxSizeBytes", MAX_SIZE_BYTES),
                    "Avatar acima do tamanho máximo");
        }
    }

    /** {@code DEVTIME-2702} / 415. */
    public static final class AvatarTypeNotAllowedException extends BusinessRuleException {
        private AvatarTypeNotAllowedException(String contentType) {
            super(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED,
                    Map.of("contentType", contentType, "allowed", ALLOWED_TYPES),
                    "Tipo de imagem não permitido");
        }
    }
}
