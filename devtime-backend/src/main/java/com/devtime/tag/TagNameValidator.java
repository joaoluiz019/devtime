package com.devtime.tag;

import com.devtime.tag.domain.TagExceptions;
import org.springframework.stereotype.Component;

/**
 * Comprimento do nome da etiqueta (RN-507).
 *
 * <p>O limite se aplica ao nome <b>normalizado</b>, não ao digitado: uma entrada de 60 caracteres
 * cheia de espaços pode encolher para 38 e é legítima (CX-05), enquanto {@code " "} normaliza para
 * vazio e é rejeitada (CX-03). Validar antes da normalização recusaria entradas válidas.
 */
@Component
public class TagNameValidator {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 40;

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2000} / {@code 422}
     */
    public void assertValid(String normalizedName) {
        int length = normalizedName.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw TagExceptions.invalidName(normalizedName); // RN-507
        }
    }
}
