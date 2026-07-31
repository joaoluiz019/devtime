package com.devtime.contract;

import java.util.Set;
import java.util.UUID;

/**
 * Origem do vínculo entre um membro e contratos (permissions.md §9).
 *
 * <p>Definição operacional do documento:
 *
 * <pre>
 * contrato C é visível para o membro M se
 *   existe work_log W com W.contract_id = C.id e W.user_id = M.id
 *   OU existe ticket T com T.contract_id = C.id e (T.assignee_id = M.id OU T.reporter_id = M.id)
 * </pre>
 *
 * <p>Ambas as fontes pertencem a features que dependem de {@code contract}; chamá-las daqui criaria
 * o ciclo que BR-008 proíbe. A inversão mantém o grafo acíclico: {@code contract} declara, {@code
 * ticket} implementa hoje e {@code worklog} acrescenta a sua parte em {@code 008}.
 */
public interface MemberContractLinkSource {

    /**
     * Contratos aos quais o membro está vinculado por esta fonte.
     *
     * @return conjunto possivelmente vazio; nunca {@code null} (ER-06)
     */
    Set<UUID> contractIdsLinkedTo(UUID userId);
}
