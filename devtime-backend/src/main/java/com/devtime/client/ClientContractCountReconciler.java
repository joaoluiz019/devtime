package com.devtime.client;

import com.devtime.client.domain.Client;
import com.devtime.shared.maintenance.DenormalizationReconciler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recalcula {@code activeContractsCount} pela contagem real (spec 003 §22.4).
 *
 * <p>O contador é ajustado por {@code +1}/{@code -1} dentro da transação de transição do contrato,
 * porque a listagem de clientes exibiria contagem divergente logo após uma ativação. O preço é este
 * job — e aqui o número errado não é apenas cosmético: {@code ClientDeletionGuard} decide pela
 * contagem, então um contador inflado impede excluir um cliente que já não tem contrato algum, e um
 * contador zerado indevidamente permitiria excluir um que tem.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientContractCountReconciler implements DenormalizationReconciler {

    private final ClientRepository repository;
    private final List<ClientContractCountSource> countSources;

    @Override
    public String target() {
        return "client.activeContractsCount";
    }

    @Override
    @Transactional
    public int reconcile() {
        if (countSources.isEmpty()) {
            // Sem a feature de contratos não há verdade contra a qual comparar, e zerar todos os
            // contadores seria destruir o dado em vez de reconciliá-lo.
            return 0;
        }
        Map<UUID, Long> real = new HashMap<>();
        countSources.forEach(
                source ->
                        source.activeContractsByClient()
                                .forEach((id, count) -> real.merge(id, count, Long::sum)));

        int corrected = 0;
        for (Client client : repository.findAll()) {
            int expected = real.getOrDefault(client.getId(), 0L).intValue();
            if (client.getActiveContractsCount() != expected) {
                log.warn(
                        "activeContractsCount divergente clientId={} persistido={} real={}",
                        client.getId(),
                        client.getActiveContractsCount(),
                        expected);
                client.setActiveContractsCount(expected);
                corrected++;
            }
        }
        return corrected;
    }
}
