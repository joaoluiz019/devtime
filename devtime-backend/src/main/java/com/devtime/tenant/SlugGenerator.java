package com.devtime.tenant;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Derivação do {@code slug} a partir do nome da organização (INV-TEN-01, CX-03, T-001-18).
 *
 * <p>Regex normativo de entities.md §6.1: {@code ^[a-z0-9]([a-z0-9-]{0,58}[a-z0-9])?$}.
 *
 * <p>A colisão é resolvida por sufixo numérico incremental, nunca por falha: CX-03 é explícito em
 * que o cadastro não pode ser recusado por causa do slug. O nome da organização é escolha do
 * usuário e pode legitimamente coincidir com o de outro tenant — recusar o cadastro transformaria
 * um detalhe de URL em barreira de entrada.
 */
@Component
public class SlugGenerator {

    static final int MAX_LENGTH = 60;

    /** Limite de tentativas antes de recorrer ao sufixo aleatório. */
    private static final int MAX_SEQUENTIAL_ATTEMPTS = 50;

    /**
     * @param name nome da organização
     * @param isTaken verifica se um candidato já existe globalmente
     * @return slug único, dentro do regex de entities.md §6.1
     */
    public String generate(String name, Predicate<String> isTaken) {
        String base = slugify(name);
        if (!isTaken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix <= MAX_SEQUENTIAL_ATTEMPTS; suffix++) {
            String candidate = withSuffix(base, String.valueOf(suffix));
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        // Cinquenta organizações homônimas é um cenário implausível, mas o cadastro não pode
        // falhar (CX-03). O sufixo aleatório encerra a busca em tempo constante.
        return withSuffix(base, UUID.randomUUID().toString().substring(0, 6));
    }

    /**
     * Converte o nome em slug, sem verificar unicidade.
     *
     * <p>Acentos são reduzidos ao caractere base (NFD + remoção de diacríticos) para que "Açaí"
     * vire "acai" e não "aca"; o restante fora de {@code [a-z0-9]} vira separador.
     */
    public String slugify(String name) {
        String source = name == null ? "" : name;
        String ascii =
                Normalizer.normalize(source, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug =
                ascii.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("-{2,}", "-")
                        .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        // Nome composto apenas por caracteres descartáveis ("---", "!!!") não produz slug algum. O
        // fallback é determinístico e satisfaz o regex; a alternativa seria recusar o cadastro por
        // causa da pontuação escolhida para o nome.
        return slug.isEmpty() ? "org" : slug;
    }

    private String withSuffix(String base, String suffix) {
        String candidate = base + "-" + suffix;
        if (candidate.length() <= MAX_LENGTH) {
            return candidate;
        }
        int keep = MAX_LENGTH - suffix.length() - 1;
        return base.substring(0, keep).replaceAll("-+$", "") + "-" + suffix;
    }
}
