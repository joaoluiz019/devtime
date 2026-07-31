package com.devtime.tag;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Normalização do nome da etiqueta (RN-506, §6.1 da spec 006).
 *
 * <p>Os cinco passos, nesta ordem exata: aparar as bordas, converter para minúsculas, colapsar
 * sequências de espaços internos, substituir cada espaço por hífen e devolver o resultado. A
 * validação do comprimento (2–40) é de {@link TagNameValidator}, porque o limite se aplica ao nome
 * <b>armazenado</b> e portanto só pode ser verificado depois desta transformação.
 *
 * <p><b>O que a normalização deliberadamente não faz</b> (§6.1 da spec):
 *
 * <ul>
 *   <li><b>Não remove acento.</b> {@code débito} e {@code debito} são palavras distintas em
 *       português; remover o acento tornaria a etiqueta ilegível a quem a digitou. A consequência
 *       aceita é que {@code refatoração} e {@code refatoracao} coexistem (CX-02).
 *   <li><b>Não filtra caractere especial.</b> {@code v2.1}, {@code api/rest} e {@code c#} são
 *       rótulos legítimos; uma allowlist exigiria uma regra de conteúdo que {@code docs/} não
 *       define.
 *   <li><b>Não aplica stemming nem singulariza.</b> {@code bug} e {@code bugs} permanecem
 *       distintos; unificá-los exigiria dicionário e produziria fusões erradas.
 * </ul>
 *
 * <p>A operação é <b>idempotente</b> (CE-02): aplicá-la ao próprio resultado devolve o mesmo valor.
 */
@Component
public class TagNormalizer {

    /** Sequência de um ou mais espaços em branco, incluindo tabulação e quebra de linha. */
    private static final String WHITESPACE_RUN = "\\s+";

    /**
     * @param rawName nome como digitado; pode ser nulo
     * @return o nome normalizado, possivelmente vazio quando a entrada só continha espaços (CX-03)
     */
    public String normalize(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.strip().toLowerCase(Locale.ROOT).replaceAll(WHITESPACE_RUN, "-");
    }
}
