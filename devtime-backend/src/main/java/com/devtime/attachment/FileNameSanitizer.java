package com.devtime.attachment;

import java.text.Normalizer;
import org.springframework.stereotype.Component;

/**
 * Sanitização do nome do arquivo (RN-804, INV-ATT-04).
 *
 * <p>Remove <i>path traversal</i> e caracteres de controle, e trunca a 255 preservando a extensão.
 * O nome original é preservado como metadado pelo serviço — nunca como caminho (§13.2).
 *
 * <p><b>Isto não é a defesa contra caminho malicioso; é a segunda camada.</b> A primeira é SG-05: a
 * {@code storageKey} é opaca e não contém nenhuma parte do nome (CP-05). Derivá-la do nome
 * reintroduziria, pela porta dos fundos, exatamente o vetor que esta classe fecha.
 */
@Component
public class FileNameSanitizer {

    /** entities.md §6.17: {@code fileName} e {@code originalFileName} são {@code String(255)}. */
    public static final int MAX_LENGTH = 255;

    /** CX-08: usado quando a sanitização não deixa nada aproveitável. */
    private static final String FALLBACK_NAME = "arquivo";

    /**
     * Caracteres reservados em caminhos de sistemas de arquivos, mais os que separam diretórios.
     *
     * <p>{@code :} entra por causa de fluxos alternativos de dados no NTFS ({@code
     * arquivo.txt:oculto}).
     */
    private static final String RESERVED_CHARACTERS = "[/\\\\:*?\"<>|]";

    /**
     * Sanitiza o nome.
     *
     * <p>CX-10: emoji é preservado — não é caractere de controle, e recusá-lo trocaria um problema
     * de segurança inexistente por um nome que o usuário não reconhece.
     */
    public String sanitize(String rawFileName) {
        if (rawFileName == null || rawFileName.isBlank()) {
            return FALLBACK_NAME;
        }
        // NFC antes de qualquer análise: sem normalização, duas sequências Unicode diferentes
        // representando o mesmo nome passariam por verificações distintas.
        String name = Normalizer.normalize(rawFileName, Normalizer.Form.NFC);

        // CX-08: `../../etc/passwd` perde tudo até o último separador. Cortar em vez de substituir
        // é o que garante que nenhuma sequência de subida sobreviva remontada.
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);

        name = stripControlCharacters(name);
        name = name.replaceAll(RESERVED_CHARACTERS, "_");

        // Um nome iniciado por ponto vira oculto em sistemas POSIX; `..` sozinho continua sendo
        // uma referência de diretório mesmo depois do corte acima.
        name = name.replaceAll("^\\.+", "");
        name = name.strip();

        if (name.isBlank()) {
            return FALLBACK_NAME;
        }
        return truncatePreservingExtension(name);
    }

    private String stripControlCharacters(String name) {
        StringBuilder clean = new StringBuilder(name.length());
        name.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .forEach(clean::appendCodePoint);
        return clean.toString();
    }

    /**
     * CX-09: nome com 300 caracteres é truncado a 255 <b>preservando a extensão</b>.
     *
     * <p>Truncar pelo fim descartaria a extensão, e o usuário perderia a única pista visual de que
     * tipo de arquivo baixou. O corte ocorre no meio do nome, não no sufixo.
     */
    private String truncatePreservingExtension(String name) {
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        // Extensão longa demais é tratada como parte do nome: `.` no meio de um nome sem sufixo
        // real não é extensão.
        if (dot <= 0 || name.length() - dot > 20) {
            return name.substring(0, MAX_LENGTH);
        }
        String extension = name.substring(dot);
        return name.substring(0, MAX_LENGTH - extension.length()) + extension;
    }

    /** O nome original também respeita o limite da coluna, sem nenhuma outra alteração. */
    public String truncateOriginal(String rawFileName) {
        if (rawFileName == null || rawFileName.isBlank()) {
            return FALLBACK_NAME;
        }
        return rawFileName.length() <= MAX_LENGTH
                ? rawFileName
                : rawFileName.substring(0, MAX_LENGTH);
    }
}
