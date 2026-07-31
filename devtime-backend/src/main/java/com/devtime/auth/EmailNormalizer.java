package com.devtime.auth;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Normalização de e-mail (RN-452, AU-03, CX-01).
 *
 * <p>Apara espaços e converte para minúsculas, sempre em {@link Locale#ROOT}. O locale explícito
 * não é preciosismo: em {@code tr-TR}, {@code "I".toLowerCase()} produz {@code "ı"}, e um servidor
 * com esse locale padrão transformaria {@code RAFAEL@X.COM} em um endereço que jamais casaria com o
 * índice {@code uq_users_email}.
 *
 * <p>A parte local <b>não</b> sofre nenhuma outra alteração (TS-001-02): remover pontos ou sufixos
 * {@code +tag} presumiria as regras de um provedor específico e recusaria endereços legítimos de
 * outro.
 */
@Component
public class EmailNormalizer {

    public String normalize(String rawEmail) {
        if (rawEmail == null) {
            return null;
        }
        return rawEmail.strip().toLowerCase(Locale.ROOT);
    }
}
