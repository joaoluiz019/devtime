package com.devtime.attachment.domain;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Allowlist fechada de tipos e suas assinaturas (RN-802, §6.2 da spec).
 *
 * <p>RS-02: a lista é <b>fechada</b>. Um tipo que não está aqui não entra — e acrescentar um exige
 * alterar {@code business-rules.md} antes do código (CG-02), porque cada tipo admitido é uma
 * decisão sobre superfície de ataque, não sobre conveniência.
 *
 * <p>A enumeração associa cada tipo à sua assinatura. É a estrutura que torna a verificação do
 * passo 8 de §6.1 uma comparação <b>cruzada</b> — assinatura encontrada × tipo declarado — e não
 * apenas "a assinatura está na lista?". OB-01: uma implementação que só verifica se a assinatura é
 * conhecida passa em todos os casos positivos e falha em todos os negativos, porque um PDF válido
 * declarado como PNG tem assinatura perfeitamente conhecida.
 */
public enum AllowedFileType {

    /** {@code 89 50 4E 47 0D 0A 1A 0A} */
    PNG("image/png", "89504E470D0A1A0A"),

    /** {@code FF D8 FF} */
    JPEG("image/jpeg", "FFD8FF"),

    /** {@code 47 49 46 38} — cobre GIF87a e GIF89a. */
    GIF("image/gif", "47494638"),

    /**
     * {@code 52 49 46 46} ({@code RIFF}) e {@code 57 45 42 50} ({@code WEBP}) no deslocamento 8.
     *
     * <p>A segunda verificação não é detalhe: {@code RIFF} sozinho também é o início de WAV e AVI.
     * Sem o segundo trecho, um áudio renomeado passaria como imagem.
     */
    WEBP("image/webp", "52494646", 8, "57454250"),

    /** {@code 25 50 44 46} ({@code %PDF}) */
    PDF("application/pdf", "25504446"),

    /** Sem assinatura — verificado por heurística de texto (§6.2, nota). */
    PLAIN_TEXT("text/plain", null),

    /** Sem assinatura — verificado por heurística de texto (§6.2, nota). */
    CSV("text/csv", null),

    /**
     * {@code 50 4B 03 04} — contêiner ZIP.
     *
     * <p>Aceito sem verificação adicional e, por §6.2, "o vetor mais provável de conteúdo
     * malicioso, coberto pelo antivírus". É o caso que torna RS-03 não negociável.
     */
    ZIP("application/zip", "504B0304"),

    /** ZIP com manifesto do Word (§6.2). */
    DOCX(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "504B0304",
            "wordprocessingml.document"),

    /** ZIP com manifesto do Excel (§6.2). */
    XLSX(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "504B0304",
            "spreadsheetml.sheet"),

    /** ZIP com manifesto do PowerPoint (§6.2). */
    PPTX(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "504B0304",
            "presentationml.presentation");

    private final String contentType;
    private final byte[] signature;
    private final int secondarySignatureOffset;
    private final byte[] secondarySignature;

    /**
     * Trecho que precisa aparecer em {@code [Content_Types].xml} para distinguir o formato Office
     * de um ZIP renomeado (CX-05). Nulo nos tipos que não são Office.
     */
    private final String manifestMarker;

    AllowedFileType(String contentType, String signatureHex) {
        this(contentType, signatureHex, -1, null, null);
    }

    AllowedFileType(String contentType, String signatureHex, String manifestMarker) {
        this(contentType, signatureHex, -1, null, manifestMarker);
    }

    AllowedFileType(
            String contentType,
            String signatureHex,
            int secondarySignatureOffset,
            String secondarySignatureHex) {
        this(contentType, signatureHex, secondarySignatureOffset, secondarySignatureHex, null);
    }

    AllowedFileType(
            String contentType,
            String signatureHex,
            int secondarySignatureOffset,
            String secondarySignatureHex,
            String manifestMarker) {
        this.contentType = contentType;
        this.signature = signatureHex == null ? null : HexFormat.of().parseHex(signatureHex);
        this.secondarySignatureOffset = secondarySignatureOffset;
        this.secondarySignature =
                secondarySignatureHex == null
                        ? null
                        : HexFormat.of().parseHex(secondarySignatureHex);
        this.manifestMarker = manifestMarker;
    }

    public String contentType() {
        return contentType;
    }

    public String manifestMarker() {
        return manifestMarker;
    }

    /** Tipos sem <i>magic number</i>: {@code text/plain} e {@code text/csv} (§6.2, nota). */
    public boolean isTextual() {
        return signature == null;
    }

    /** Formatos Office, que exigem inspeção do manifesto interno além da assinatura. */
    public boolean requiresManifestCheck() {
        return manifestMarker != null;
    }

    /**
     * RN-802, passo 7: o {@code contentType} declarado está na allowlist?
     *
     * <p>A comparação ignora parâmetros ({@code ; charset=utf-8}) e caixa, porque ambos são
     * variação legítima do cabeçalho e nenhum altera o tipo. Não ignora espaços internos nem
     * qualquer outra diferença: a lista é fechada e a tolerância termina aqui.
     */
    public static Optional<AllowedFileType> ofContentType(String declaredContentType) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return Optional.empty();
        }
        String normalized = declaredContentType.split(";")[0].trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.contentType.equals(normalized))
                .findFirst();
    }

    /**
     * A assinatura declarada por este tipo aparece no início do conteúdo?
     *
     * <p>Devolve {@code false} para os tipos textuais: eles não têm assinatura, e responder {@code
     * true} os faria "coincidir" com qualquer conteúdo, inclusive um executável.
     */
    public boolean matchesSignature(byte[] header) {
        if (signature == null || header.length < signature.length) {
            return false;
        }
        if (!startsWith(header, signature, 0)) {
            return false;
        }
        if (secondarySignature == null) {
            return true;
        }
        return header.length >= secondarySignatureOffset + secondarySignature.length
                && startsWith(header, secondarySignature, secondarySignatureOffset);
    }

    private static boolean startsWith(byte[] header, byte[] expected, int offset) {
        for (int index = 0; index < expected.length; index++) {
            if (header[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }
}
