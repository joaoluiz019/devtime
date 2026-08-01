package com.devtime.contract;

import com.devtime.contract.dto.BalanceResponses.PeriodSnapshotResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura e verificação de snapshots (spec 011 §22.2).
 *
 * <p>A geração pertence a {@code PeriodClosingService} — ela é o passo 4 de uma sequência atômica e
 * não faz sentido isolada. Aqui ficam a leitura e a verificação de integridade, que são operações
 * independentes e sob demanda.
 */
public interface SnapshotService {

    /**
     * Snapshot mais recente do período, com o checksum já verificado.
     *
     * <p>CX-21: quando o checksum diverge, a resposta traz {@code checksumValid = false} e o fato é
     * registrado como {@code ERROR}. O snapshot <b>não</b> é corrigido: reescrevê-lo para "acertar"
     * o checksum destruiria a única prova de que houve alteração.
     */
    Optional<PeriodSnapshotResponse> latest(UUID periodId);

    /**
     * RN-701: payload congelado do período fechado, para {@code 012-reports}.
     *
     * <p>Relatórios de período fechado são servidos exclusivamente daqui, o que os torna imunes a
     * qualquer degradação de {@code work_logs} — e, mais importante, imutáveis: o relatório de dois
     * anos atrás é byte a byte o que foi entregue ao cliente.
     */
    Optional<String> payloadForReport(UUID periodId);

    /** SG-05: recalcula o checksum e compara com o persistido. */
    boolean verifyChecksum(UUID snapshotId);
}
