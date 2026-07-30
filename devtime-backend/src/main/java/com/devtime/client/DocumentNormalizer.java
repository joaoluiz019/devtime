package com.devtime.client;

import org.springframework.stereotype.Component;

/**
 * Remove a máscara do documento, preservando apenas dígitos (CX-03).
 *
 * <p>A normalização ocorre <b>antes</b> da validação e da persistência: o mesmo documento digitado
 * como {@code 12.345.678/0001-90} ou {@code 12345678000190} precisa colidir com a unicidade de
 * RN-403, o que só acontece se ambos forem armazenados na mesma forma.
 */
@Component
public class DocumentNormalizer {

    /**
     * @return apenas os dígitos do valor, ou {@code null} se a entrada for nula ou vazia após a
     *     remoção — cliente sem documento é caso previsto (CE-C-01)
     */
    public String normalize(String rawDocument) {
        if (rawDocument == null) {
            return null;
        }
        String digits = rawDocument.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }
}
