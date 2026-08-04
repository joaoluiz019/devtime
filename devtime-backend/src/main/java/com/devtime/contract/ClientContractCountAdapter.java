package com.devtime.contract;

import com.devtime.client.ClientContractCountSource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contribuição de {@code 004} à reconciliação de {@code activeContractsCount} de {@code 003}.
 *
 * <p>Nenhuma aresta nova no grafo: {@code contract} já depende de {@code client} (RN-201). Quem
 * declara a interface é quem precisa do dado; quem a implementa é quem o possui — o mesmo arranjo
 * de {@code WorkLogSourceAdapters}.
 *
 * <p>A projeção é feita aqui, e não em JPQL com {@code SELECT new}: são dois escalares e um record
 * de topo só para transportá-los seria cerimônia sem leitor.
 */
@Component
@RequiredArgsConstructor
public class ClientContractCountAdapter implements ClientContractCountSource {

    private final ContractRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> activeContractsByClient() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : repository.countActiveByClient()) {
            counts.merge((UUID) row[0], ((Number) row[1]).longValue(), Long::sum);
        }
        return counts;
    }
}
