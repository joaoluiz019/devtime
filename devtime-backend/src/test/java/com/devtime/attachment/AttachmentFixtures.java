package com.devtime.attachment;

import com.devtime.attachment.domain.UploadContent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Conteúdos de teste dos 9 tipos da allowlist (§6.2) e dos vetores de burla (§19).
 *
 * <p>BR-206/BR-207: nada aleatório, nada por SQL bruto. Cada arquivo é montado a partir da
 * assinatura documentada, e não copiado de um arquivo real — é o que garante que o teste verifique
 * a regra escrita em {@code business-rules.md}, e não o que um arquivo específico por acaso contém.
 */
public final class AttachmentFixtures {

    /**
     * Arquivo de teste EICAR — <b>não</b> é malware.
     *
     * <p>É a cadeia padrão do EICAR, reconhecida por todo antivírus como se fosse uma ameaça,
     * criada justamente para testar a cadeia de detecção sem usar código malicioso real. DoD-02 e
     * §9 de {@code implementation-order.md} a exigem: sem ela, a proteção contra arquivo malicioso
     * é uma suposição.
     *
     * <p>Montada por concatenação para que nenhum verificador do próprio repositório de código a
     * detecte como ameaça no arquivo-fonte.
     */
    public static final String EICAR_SIGNATURE =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-" + "ANTIVIRUS-TEST-FILE!" + "$H+H*";

    private AttachmentFixtures() {}

    public static byte[] png() {
        return withHeader("89504E470D0A1A0A", "conteúdo de imagem");
    }

    public static byte[] jpeg() {
        return withHeader("FFD8FF", "conteúdo de imagem");
    }

    public static byte[] gif() {
        return withHeader("47494638", "conteúdo de imagem");
    }

    /** {@code RIFF} + tamanho + {@code WEBP} no deslocamento 8. */
    public static byte[] webp() {
        byte[] header = HexFormat.of().parseHex("52494646" + "00000000" + "57454250");
        return concat(header, "conteúdo de imagem".getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] pdf() {
        return withHeader("25504446", "-1.7 conteúdo de documento");
    }

    public static byte[] plainText() {
        return "linha um\nlinha dois\tcom tabulação\nacentuação: ação\n"
                .getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] csv() {
        return "coluna,valor\nhoras,10\n".getBytes(StandardCharsets.UTF_8);
    }

    /** ZIP legítimo, sem manifesto Office. */
    public static byte[] zip() {
        return zipWith("leiame.txt", "conteúdo");
    }

    /** Documento Office válido: contêiner ZIP com {@code [Content_Types].xml} coerente. */
    public static byte[] office(String manifestMarker) {
        return zipWith(
                "[Content_Types].xml",
                "<?xml version=\"1.0\"?><Types><Override ContentType=\""
                        + "application/vnd.openxmlformats-officedocument."
                        + manifestMarker
                        + ".main+xml\"/></Types>");
    }

    /** CX-05: ZIP renomeado para {@code .docx} — assinatura idêntica, manifesto ausente. */
    public static byte[] zipWithoutOfficeManifest() {
        return zip();
    }

    /**
     * CX-03 / SG-01: executável do Windows, renomeado para o que se quiser.
     *
     * <p>Não é apenas {@code MZ} seguido de texto: o cabeçalho PE real traz bytes nulos e o
     * deslocamento do cabeçalho estendido. A diferença importa — {@code 4D 5A} são dois caracteres
     * ASCII imprimíveis, e um executável reduzido a eles passaria pela heurística de texto de §6.2
     * fazendo o teste de {@code text/plain} verde por acidente. É exatamente o modo de falha que
     * OB-04 descreve.
     */
    public static byte[] windowsExecutable() {
        byte[] dosHeader =
                HexFormat.of()
                        .parseHex(
                                "4D5A90000300000004000000FFFF0000"
                                        + "B800000000000000400000000000000000000000000000000000"
                                        + "0000000000000000000000000000000080000000");
        return concat(dosHeader, "PE\0\0".getBytes(StandardCharsets.US_ASCII), new byte[64]);
    }

    /** CX-07: texto com byte nulo — heurística de binário de §6.2. */
    public static byte[] textWithNullByte() {
        return concat(
                "linha comum\n".getBytes(StandardCharsets.UTF_8),
                new byte[] {0x00},
                "resto".getBytes(StandardCharsets.UTF_8));
    }

    /** CX-02. */
    public static byte[] empty() {
        return new byte[0];
    }

    /** Arquivo EICAR isolado, com o tipo textual da allowlist. */
    public static byte[] eicar() {
        return EICAR_SIGNATURE.getBytes(StandardCharsets.US_ASCII);
    }

    /** CA-09 / SG-03: EICAR dentro de um ZIP legítimo. */
    public static byte[] eicarInZip() {
        return zipWith("eicar.com", EICAR_SIGNATURE);
    }

    /** Conteúdo PNG de tamanho arbitrário, para os testes de limite de RN-801. */
    public static byte[] pngOfSize(int totalBytes) {
        byte[] signature = HexFormat.of().parseHex("89504E470D0A1A0A");
        byte[] content = new byte[totalBytes];
        System.arraycopy(signature, 0, content, 0, Math.min(signature.length, totalBytes));
        // Preenchimento não nulo: bytes nulos são legítimos em PNG, mas manter o conteúdo
        // determinístico evita que o checksum dependa do que a JVM zerou.
        for (int index = signature.length; index < totalBytes; index++) {
            content[index] = (byte) 0x41;
        }
        return content;
    }

    /** Adapta um vetor de bytes a {@link UploadContent}, como o controller faz com o multipart. */
    public static UploadContent upload(String fileName, String contentType, byte[] content) {
        return new ByteArrayUploadContent(fileName, contentType, content);
    }

    private static byte[] withHeader(String signatureHex, String body) {
        return concat(HexFormat.of().parseHex(signatureHex), body.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zipWith(String entryName, String content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] joined = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }
        return joined;
    }

    /**
     * {@link UploadContent} sobre um vetor em memória.
     *
     * <p>Aceitável em teste — e apenas em teste. Em produção CP-14 proíbe carregar o arquivo
     * inteiro; aqui os conteúdos têm dezenas de bytes, exceto os de limite, que existem
     * precisamente para verificar o limite.
     */
    private record ByteArrayUploadContent(String fileName, String contentType, byte[] content)
            implements UploadContent {

        @Override
        public String originalFileName() {
            return fileName;
        }

        @Override
        public String declaredContentType() {
            return contentType;
        }

        @Override
        public long sizeBytes() {
            return content.length;
        }

        @Override
        public InputStream openStream() {
            // Fluxo novo a cada chamada, como MultipartFile#getInputStream.
            return new ByteArrayInputStream(content);
        }
    }
}
