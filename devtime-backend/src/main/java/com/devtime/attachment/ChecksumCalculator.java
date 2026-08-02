package com.devtime.attachment;

import com.devtime.attachment.domain.UploadContent;
import com.devtime.shared.storage.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * SHA-256 do conteúdo, calculado em <b>fluxo</b> (RN-805, CP-14).
 *
 * <p>§20.1: o arquivo nunca é carregado em memória. Um upload de 10 MB carregado integralmente
 * seria viável; com uploads concorrentes, não — e a alternativa "não é lenta, <b>falha</b>". R-07
 * classifica o esgotamento de memória em uploads concorrentes como risco de impacto alto, com
 * gatilho explícito: {@code OutOfMemory} em produção.
 *
 * <p>O checksum tem dois usos, e é importante que sejam o mesmo valor: deduplicação dentro do
 * tenant (§6.4) e integridade. Ele <b>nunca</b> é exposto em resposta (CP-07): permitiria verificar
 * se um arquivo específico existe no tenant sem tê-lo, que é o canal de inferência que §6.4 evita.
 */
@Component
public class ChecksumCalculator {

    /** 8 KB por leitura, o mesmo tamanho da janela de assinatura. */
    private static final int BUFFER_SIZE = 8192;

    /** Hexadecimal <b>minúsculo</b>, formato exigido pelo {@code CHECK} de {@code V023}. */
    public String sha256(UploadContent content) {
        try (InputStream raw = content.openStream();
                DigestInputStream digesting = new DigestInputStream(raw, newDigest())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            // O retorno é descartado de propósito: o que interessa é o efeito colateral no digest.
            // Nada do conteúdo é retido entre iterações.
            while (digesting.read(buffer) != -1) {
                // fluxo consumido apenas para alimentar o digest
            }
            return HexFormat.of().formatHex(digesting.getMessageDigest().digest());
        } catch (IOException unreadable) {
            throw new StorageException("falha ao calcular checksum do conteúdo", unreadable);
        }
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 é obrigatório em toda implementação da plataforma Java.
            throw new IllegalStateException("SHA-256 indisponível na JVM", impossible);
        }
    }
}
