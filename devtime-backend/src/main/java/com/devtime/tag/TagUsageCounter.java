package com.devtime.tag;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Contador desnormalizado de uso (INV-TAG-04).
 *
 * <p>A atualização ocorre <b>dentro</b> da transação do vínculo, como {@code activeContractsCount}
 * em {@code 003-clients}. Fora dela, a listagem ordenada por uso exibiria contagem divergente logo
 * após rotular um ticket, e o filtro {@code minUsage} devolveria resultados inconsistentes (§15 da
 * spec).
 *
 * <p>Toda alteração é um {@code UPDATE ... SET usage_count = usage_count + ?}: ler o valor e gravar
 * o resultado perderia atualizações sob dois vínculos simultâneos à mesma etiqueta popular.
 * Divergência residual sob contenção extrema é corrigida pela reconciliação (CX-13).
 */
@Component
@RequiredArgsConstructor
public class TagUsageCounter {

    private final TagRepository repository;

    public void increment(Collection<java.util.UUID> tagIds) {
        tagIds.forEach(tagId -> repository.adjustUsageCount(tagId, 1));
    }

    public void decrement(Collection<java.util.UUID> tagIds) {
        tagIds.forEach(tagId -> repository.adjustUsageCount(tagId, -1));
    }
}
