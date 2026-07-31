package com.devtime.tag;

import com.devtime.tag.domain.TagExceptions;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Unicidade do nome normalizado no tenant (RN-507, INV-TAG-02).
 *
 * <p>A verificação usa o nome <b>já normalizado</b>. Comparar o nome bruto permitiria que {@code
 * Code Review} e {@code code-review} passassem como registros distintos e colidissem depois no
 * índice único — produzindo erro de constraint em vez da mensagem de negócio correta (§6.2 da
 * spec).
 *
 * <p>O índice único parcial {@code uq_tags_tenant_name} permanece como barreira final para a
 * corrida entre duas criações simultâneas; esta validação existe para produzir {@code DEVTIME-2604}
 * no caso comum, não para substituí-lo.
 */
@Component
@RequiredArgsConstructor
public class TagUniquenessValidator {

    private final TagRepository repository;

    /**
     * @param excludedId identificador ignorado na verificação; usado na renomeação, em que o
     *     próprio registro não é conflito
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2604} / {@code 409}
     */
    public void assertUnique(String normalizedName, UUID excludedId) {
        if (repository.existsByNormalizedName(normalizedName, excludedId)) {
            throw TagExceptions.duplicateName(); // RN-507
        }
    }
}
