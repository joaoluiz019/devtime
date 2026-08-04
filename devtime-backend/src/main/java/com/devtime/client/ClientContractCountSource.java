package com.devtime.client;

import java.util.Map;
import java.util.UUID;

/**
 * Contagem real de contratos ativos por cliente (entities.md §9, spec 003 §22.4).
 *
 * <p>{@code contract} depende de {@code client} — RN-201 exige cliente {@code ACTIVE} —, então
 * {@code client} não pode consultá-lo de volta sem fechar o ciclo que AR-09 proíbe. A inversão é a
 * mesma de {@code TicketWorkLogCountSource}: quem precisa do dado declara, quem o possui
 * implementa.
 *
 * <p>Sem implementação registrada o mapa é vazio e a reconciliação zeraria todos os contadores — o
 * que é correto num sistema sem a feature de contratos, e por isso o reconciliador só existe
 * enquanto ela existir.
 */
public interface ClientContractCountSource {

    /**
     * Contratos {@code ACTIVE} por cliente, em todos os tenants.
     *
     * <p>Em lote e não por cliente (CP-12): o reconciliador percorre a base inteira, e uma consulta
     * por cliente transformaria o job noturno em milhares de idas ao banco.
     *
     * <p>Clientes sem contrato ativo não aparecem no resultado — a ausência significa zero, e
     * materializar uma linha zerada por cliente custaria memória proporcional à base toda.
     */
    Map<UUID, Long> activeContractsByClient();
}
