package com.devtime.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Token opaco e digestão (TS-001-05, RT-01, RT-02). */
class OpaqueTokenGeneratorTest {

    private final OpaqueTokenGenerator generator = new OpaqueTokenGenerator();

    @Test
    @DisplayName("RT-01: o token possui 256 bits de entropia, em Base64 URL-safe")
    void shouldGenerate256BitToken() {
        String token = generator.generate();

        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
        assertThat(token)
                .as("o valor viaja em cookie e em link de e-mail; +, / e = exigiriam escape")
                .doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("RT-01: 10.000 tokens gerados não colidem")
    void shouldNotCollide() {
        Set<String> tokens = new HashSet<>();
        IntStream.range(0, 10_000).forEach(index -> tokens.add(generator.generate()));

        assertThat(tokens).hasSize(10_000);
    }

    @Test
    @DisplayName("RT-02: o hash é SHA-256 em 64 caracteres hexadecimais e é determinístico")
    void hashMustBeStableSha256Hex() {
        String token = generator.generate();

        assertThat(generator.hash(token))
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(generator.hash(token));
    }

    @Test
    @DisplayName("RT-02: tokens distintos produzem hashes distintos")
    void differentTokensMustProduceDifferentHashes() {
        assertThat(generator.hash(generator.generate()))
                .isNotEqualTo(generator.hash(generator.generate()));
    }

    @Test
    @DisplayName("RT-02: token vazio não possui hash — evita casar com registro corrompido")
    void blankTokenMustBeRejected() {
        assertThatThrownBy(() -> generator.hash("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.hash(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
