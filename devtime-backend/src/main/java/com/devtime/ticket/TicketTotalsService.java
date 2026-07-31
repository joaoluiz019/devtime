package com.devtime.ticket;

import java.util.UUID;

/**
 * Totais desnormalizados do ticket (RN-308, INV-TCK-05).
 *
 * <p>Interface pública consumida por {@code 008-worklogs}, aplicada <b>dentro</b> da transação do
 * work log: um total divergente aparece imediatamente na listagem, então ele e o registro de horas
 * precisam ser o mesmo fato.
 */
public interface TicketTotalsService {

    /**
     * Aplica a variação dos totais por incremento.
     *
     * <p>CP-12: reagregar todos os work logs do ticket é proibido. RN-308 dispara em toda escrita
     * de work log — o caminho mais quente do sistema —, e a diferença entre incrementar e reagregar
     * é a diferença entre custo constante e custo linear no número de registros.
     *
     * @param spentDelta variação de {@code spentMinutes}; negativa em exclusão de work log
     * @param billableDelta variação de {@code billableMinutes}
     */
    void applyWorkLogDelta(UUID ticketId, int spentDelta, int billableDelta);
}
