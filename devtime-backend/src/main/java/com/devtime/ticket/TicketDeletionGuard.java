package com.devtime.ticket;

import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Exclusão restrita de ticket (RN-307, INV-TCK-03).
 *
 * <p>Excluir um ticket com horas destruiria o vínculo de registros já apurados — e a hierarquia
 * {@code WorkLog → Ticket → Contract → Client} é o que torna cada hora justificável ao cliente
 * (ART-003). O caminho correto é cancelar: as horas permanecem e continuam nos relatórios (RN-314).
 *
 * <p>Por isso a mensagem de erro <b>sugere a ação alternativa</b> em vez de apenas recusar: sem a
 * sugestão, o usuário tenta de novo ou desiste, e o cancelamento — que resolve o caso dele — não é
 * descoberto.
 */
@Component
@RequiredArgsConstructor
public class TicketDeletionGuard {

    private final TicketWorkLogGate workLogGate;

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2307} / {@code 409}
     */
    public void assertDeletable(Ticket ticket) {
        if (workLogGate.hasWorkLogs(ticket)) {
            throw TicketExceptions.deleteHasWorkLogs(ticket.getSpentMinutes()); // RN-307
        }
    }
}
