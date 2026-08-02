package com.devtime.attachment;

import com.devtime.attachment.domain.AllowedFileType;
import com.devtime.attachment.domain.AttachmentExceptions;
import com.devtime.attachment.domain.UploadContent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Passos 7 e 8 de §6.1 — allowlist e assinatura binária (RN-802, INV-ATT-03).
 *
 * <p><b>É a classe mais importante da feature</b> (OB-01). Os dois passos são defesas de naturezas
 * diferentes e permanecem separados de propósito: o passo 7 confia no que o cliente <b>declara</b>;
 * o passo 8 verifica o que o arquivo <b>é</b>. Um executável renomeado para {@code .pdf} com {@code
 * contentType: application/pdf} passa em 7 e falha em 8. Sem o passo 8, a allowlist é convenção.
 *
 * <p>A verificação do passo 8 é <b>cruzada</b>: não pergunta "esta assinatura é conhecida?", e sim
 * "esta assinatura é a do tipo declarado?". A primeira forma aceitaria um PDF válido declarado como
 * {@code image/png} (CX-04), porque a assinatura do PDF é perfeitamente conhecida.
 *
 * <p>§20: lê apenas os primeiros 8 KB. O restante do arquivo não é tocado aqui — quem o percorre
 * são o checksum e o antivírus.
 */
@Component
@Slf4j
public class MagicNumberValidator {

    /** §20: janela de leitura da assinatura e da heurística de texto. */
    static final int HEADER_SIZE = 8192;

    /**
     * Teto de leitura do manifesto Office.
     *
     * <p>CP-17 proíbe descomprimir o ZIP na aplicação (SG-14, bomba de descompressão), e §6.2 exige
     * ler o manifesto interno. As duas coexistem porque aqui se lê <b>uma única entrada</b>, com
     * teto rígido de bytes: um {@code [Content_Types].xml} legítimo tem poucos KB, e nenhuma bomba
     * de descompressão sobrevive a um limite que não depende do que o arquivo declara.
     */
    private static final int MANIFEST_READ_LIMIT = 64 * 1024;

    private static final String OFFICE_MANIFEST_ENTRY = "[Content_Types].xml";

    /**
     * Caracteres de controle aceitos em texto: tabulação, avanço de linha, retorno de carro e
     * avanço de página. Qualquer outro indica binário (CX-07).
     */
    private static final byte TAB = 0x09;

    private static final byte LINE_FEED = 0x0A;
    private static final byte FORM_FEED = 0x0C;
    private static final byte CARRIAGE_RETURN = 0x0D;

    /**
     * Aplica os passos 7 e 8 na ordem normativa.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2702} / {@code 415} em
     *     qualquer das duas falhas
     */
    public AllowedFileType assertDeclaredTypeMatchesContent(UploadContent content) {
        // Passo 7 — RN-802: o que o cliente declara está na allowlist?
        AllowedFileType declared =
                AllowedFileType.ofContentType(content.declaredContentType())
                        .orElseThrow(
                                () -> {
                                    // §28: INFO. É erro de cliente com a mesma frequência que é
                                    // tentativa — o nome do arquivo nunca entra (CP-19).
                                    log.info(
                                            "tipo rejeitado pela allowlist contentTypeDeclarado={}",
                                            content.declaredContentType());
                                    return AttachmentExceptions.unsupportedFileType(
                                            content.declaredContentType());
                                });

        // Passo 8 — RN-802: o que o arquivo é coincide com o que foi declarado?
        byte[] header = readHeader(content);
        if (!matchesContent(declared, header, content)) {
            // §28: WARN, e não INFO. Pode ser erro de cliente, mas é o padrão de um ataque —
            // e a métrica attachment.rejected.signature alerta acima de 5 por dia.
            log.warn(
                    "assinatura divergente do tipo declarado contentTypeDeclarado={}"
                            + " assinaturaEncontrada={}",
                    declared.contentType(),
                    describeSignature(header));
            throw AttachmentExceptions.contentTypeMismatch(content.declaredContentType());
        }
        return declared;
    }

    private boolean matchesContent(AllowedFileType declared, byte[] header, UploadContent content) {
        if (declared.isTextual()) {
            // §6.2, nota: sem magic number, a verificação é ausência de binário + UTF-8 válido.
            return isPlausibleText(header);
        }
        if (!declared.matchesSignature(header)) {
            return false;
        }
        if (!declared.requiresManifestCheck()) {
            return true;
        }
        // CX-05: ZIP e Office compartilham a assinatura; só o manifesto os distingue.
        return hasOfficeManifest(content, declared);
    }

    /**
     * Heurística de texto (§6.2, nota; CX-02, CX-07).
     *
     * <p>Duas verificações: (a) ausência de byte nulo e de caractere de controle atípico nos
     * primeiros 8 KB; (b) decodificação válida em UTF-8. Um executável enviado como {@code
     * text/plain} falha em (a).
     *
     * <p>OB-04 é explícito quanto ao alcance disto: é a categoria mais fraca da allowlist e a
     * heurística é contornável. Ela está aqui porque log e CSV são anexos legítimos e frequentes; a
     * mitigação real é o antivírus, e é este ponto que torna RS-03 não negociável.
     */
    boolean isPlausibleText(byte[] header) {
        // CX-02: arquivo de 0 byte não é texto plausível — nem qualquer outra coisa.
        if (header.length == 0) {
            return false;
        }
        for (byte value : header) {
            if (isDisallowedControlByte(value)) {
                return false;
            }
        }
        return isValidUtf8(header);
    }

    private boolean isDisallowedControlByte(byte value) {
        if (value >= 0x20 || value < 0) {
            // Imprimível ASCII ou byte alto (continuação UTF-8, validada adiante).
            return false;
        }
        return value != TAB && value != LINE_FEED && value != FORM_FEED && value != CARRIAGE_RETURN;
    }

    /**
     * Decodificação estrita em UTF-8.
     *
     * <p>Um caractere multibyte pode ficar partido no corte de 8 KB, e isso <b>não</b> é conteúdo
     * inválido. A distinção é a razão de usar a forma de três argumentos de {@code decode}: com
     * {@code endOfInput = false}, uma sequência truncada devolve {@code UNDERFLOW} — entrada
     * incompleta, e não malformada —, enquanto um byte realmente inválido devolve {@code
     * isMalformed()}. A forma de um argumento não faz essa distinção e reprovaria todo arquivo de
     * texto cujo caractere acentuado caísse exatamente no limite da janela.
     */
    private boolean isValidUtf8(byte[] header) {
        var decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        CoderResult result =
                decoder.decode(ByteBuffer.wrap(header), CharBuffer.allocate(header.length), false);
        return !result.isMalformed() && !result.isUnmappable();
    }

    /**
     * CX-05: o manifesto interno distingue um {@code .docx} legítimo de um ZIP renomeado.
     *
     * <p>Percorre as entradas até encontrar {@code [Content_Types].xml} e lê no máximo {@link
     * #MANIFEST_READ_LIMIT} bytes dela. Nenhuma outra entrada é lida, e nada é escrito em disco.
     */
    private boolean hasOfficeManifest(UploadContent content, AllowedFileType declared) {
        try (InputStream raw = content.openStream();
                ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!OFFICE_MANIFEST_ENTRY.equals(entry.getName())) {
                    continue;
                }
                String manifest = readLimited(zip);
                return manifest.contains(declared.manifestMarker());
            }
            return false;
        } catch (IOException | IllegalArgumentException unreadable) {
            // Um contêiner que não abre não é um Office válido. Falhar fechado é a única leitura
            // segura: tratar "não consegui verificar" como "está certo" é o inverso de AV-02.
            log.warn("manifesto Office ilegível contentTypeDeclarado={}", declared.contentType());
            return false;
        }
    }

    private String readLimited(InputStream in) throws IOException {
        byte[] buffer = new byte[MANIFEST_READ_LIMIT];
        int total = 0;
        int read;
        while (total < buffer.length
                && (read = in.read(buffer, total, buffer.length - total)) != -1) {
            total += read;
        }
        return new String(buffer, 0, total, StandardCharsets.UTF_8);
    }

    /** Lê os primeiros bytes. Nunca carrega o arquivo inteiro (CP-14, §20). */
    private byte[] readHeader(UploadContent content) {
        try (InputStream in = content.openStream()) {
            return in.readNBytes(HEADER_SIZE);
        } catch (IOException unreadable) {
            // Conteúdo ilegível não passa: falhar fechado (CG-06).
            log.warn("conteúdo ilegível na verificação de assinatura");
            throw AttachmentExceptions.contentTypeMismatch(content.declaredContentType());
        }
    }

    /**
     * Primeiros bytes em hexadecimal, para o log de assinatura divergente.
     *
     * <p>Oito bytes: o suficiente para identificar a assinatura real e curto o bastante para não
     * registrar conteúdo do arquivo, que §19.1 proíbe.
     */
    private String describeSignature(byte[] header) {
        int length = Math.min(8, header.length);
        StringBuilder hex = new StringBuilder(length * 2);
        for (int index = 0; index < length; index++) {
            hex.append(String.format("%02X", header[index]));
        }
        return hex.toString();
    }
}
