package com.devtime.ticket;

import com.devtime.ticket.dto.TicketRequests.TicketAssignRequest;
import com.devtime.ticket.dto.TicketRequests.TicketTransitionRequest;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import java.util.UUID;

/**
 * Transições e atribuição do ticket (spec 007 §22.2).
 *
 * <p>ME-05: a situação é alterada exclusivamente por estes métodos, nunca por {@code PATCH} no
 * campo {@code status}. Permitir a atualização direta transformaria uma operação com guardas e
 * efeitos em uma escrita de campo, abrindo caminho para estados inconsistentes — um ticket em
 * {@code DONE} sem {@code completedAt}, por exemplo (INV-TCK-04).
 */
public interface TicketTransitionService {

    /** Aplica a matriz §4.7 e suas guardas (RN-310, RN-311, {@code blockReason}). */
    TicketResponse transition(UUID id, TicketTransitionRequest request);

    /** RN-304: responsável precisa ser membership {@code ACTIVE}; nulo remove (FA-05). */
    TicketResponse assign(UUID id, TicketAssignRequest request);

    /**
     * RN-312: ticket {@code DONE} que recebe work log volta a {@code IN_PROGRESS}.
     *
     * <p>Interface pública para {@code 008-worklogs}, aplicada <b>dentro</b> da transação do work
     * log. Em qualquer outra situação é uma operação sem efeito.
     *
     * <p>CX-06: a reabertura <b>não</b> é revertida quando o work log que a causou é excluído.
     * Reverter exigiria armazenar o estado anterior e decidir o que fazer se houver outras
     * alterações no intervalo — complexidade desproporcional a um caso raro. O usuário reconclui.
     */
    void reopenOnWorkLog(UUID ticketId, UUID workLogId);
}
