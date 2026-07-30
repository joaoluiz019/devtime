package com.devtime.client;

import com.devtime.client.domain.Client;
import com.devtime.client.domain.ClientExceptions;
import org.springframework.stereotype.Component;

/**
 * RN-401: cliente com contrato {@code ACTIVE} ou {@code SUSPENDED} não pode ser excluído.
 *
 * <p>A verificação usa {@code client.activeContractsCount} — campo desnormalizado mantido pelas
 * transições de {@code 004-contracts} (entities.md §9) — e não uma consulta a {@code
 * ContractService}, como sugere T-003-10 da spec.
 *
 * <p><b>Motivo:</b> {@code 004} já depende de {@code 003} ({@code
 * ClientService.getActiveForContract}, RN-201). Fazer {@code 003} consultar {@code ContractService}
 * fecharia um ciclo entre os pacotes de feature, proibido por AR-09 e verificado por ArchUnit — um
 * ciclo impede extrair qualquer um dos dois módulos. O contador expressa exatamente o conjunto que
 * RN-401 protege: contratos são somados na ativação e subtraídos no encerramento ou cancelamento,
 * de modo que {@code ACTIVE} e {@code SUSPENDED} permanecem contados e {@code ENDED}/{@code
 * CANCELLED} não (CE-C-06).
 *
 * <p>A convergência do contador é garantida pela reconciliação noturna prevista em §9 de
 * entities.md.
 */
@Component
public class ClientDeletionGuard {

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2401} / {@code 409}
     */
    public void assertDeletable(Client client) {
        if (client.getActiveContractsCount() > 0) {
            throw ClientExceptions.hasActiveContracts(client.getActiveContractsCount()); // RN-401
        }
    }
}
