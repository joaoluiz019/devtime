package com.devtime.contract;

import com.devtime.contract.dto.ContractResponses.ClientContractSummaryResponse;
import java.util.UUID;

/**
 * Consolidação de contratos e períodos por cliente (clients.md §8).
 *
 * <p>Alimenta a tela de detalhe do cliente e o gráfico de tendência.
 */
public interface ClientContractSummaryService {

    ClientContractSummaryResponse summarize(UUID clientId, int periods);
}
