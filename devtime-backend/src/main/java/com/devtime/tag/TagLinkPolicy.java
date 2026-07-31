package com.devtime.tag;

import com.devtime.tag.domain.TagExceptions;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Limite de etiquetas por alvo (RN-313, INV-TAG-01).
 *
 * <p>O limite é verificado sobre o <b>conjunto resultante</b>, não sobre o número de adições: um
 * ticket com 10 etiquetas pode trocar uma por outra sem violar a regra (CX-11), e pode ser editado
 * sem tocar nelas (CX-14 de 007).
 */
@Component
public class TagLinkPolicy {

    /** RN-313: dez por ticket e dez por work log. */
    public static final int MAX_TAGS_PER_TARGET = 10;

    /**
     * @param targetId ticket ou work log rotulado, devolvido no corpo do erro para a UI destacar
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2313} / {@code 422}
     */
    public void assertWithinLimit(UUID targetId, int resultingCount) {
        if (resultingCount > MAX_TAGS_PER_TARGET) {
            throw TagExceptions.limitExceeded(targetId, resultingCount, MAX_TAGS_PER_TARGET);
        }
    }
}
