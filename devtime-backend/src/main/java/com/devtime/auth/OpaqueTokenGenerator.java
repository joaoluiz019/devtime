package com.devtime.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Geração e digestão dos tokens opacos (RT-01, RT-02, T-001-12).
 *
 * <p>RT-01: 256 bits de {@link SecureRandom}, codificados em Base64 URL-safe sem preenchimento — o
 * valor viaja em cookie e em link de e-mail, onde {@code +}, {@code /} e {@code =} exigiriam
 * escape.
 *
 * <p>RT-02: apenas o SHA-256 é persistido. SHA-256 e não BCrypt: o token já é aleatório de 256
 * bits, portanto não há espaço de busca a encarecer, e a comparação ocorre em toda renovação — o
 * custo de BCrypt seria pago sem contrapartida de segurança. A digestão é o que impede que um
 * vazamento do banco entregue sessões ativas.
 */
@Component
public class OpaqueTokenGenerator {

    private static final int TOKEN_BYTES = 32; // 256 bits (RT-01)

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * @return o valor bruto, entregue ao cliente uma única vez e nunca persistido
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }

    /**
     * @return SHA-256 em hexadecimal minúsculo, com exatamente 64 caracteres
     */
    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token vazio não possui hash");
        }
        return HexFormat.of().formatHex(digest().digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é obrigatório em toda JVM: a ausência é falha de ambiente, não condição de
            // execução tratável.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
