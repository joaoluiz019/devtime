package com.devtime.ticket;

import com.devtime.ticket.domain.Ticket;
import org.springframework.stereotype.Component;

/**
 * Existência de horas registradas no ticket, consultada por RN-305 e RN-307.
 *
 * <p><b>Estado nesta sprint:</b> {@code 008-worklogs} não foi implementada e a tabela {@code
 * work_logs} não existe (database.md §8.1, V016). Sem {@code WorkLogService} para consultar — e
 * BR-002 proíbe alcançar um repositório de outra feature —, a existência de horas é derivada de
 * {@code spentMinutes}.
 *
 * <p><b>Por que a derivação é exata, e não uma aproximação:</b> RN-115 exige {@code netMinutes > 0}
 * em todo work log, e RN-308 mantém {@code spentMinutes} como a soma desses valores, reduzida na
 * exclusão (RN-125). Logo {@code spentMinutes > 0} se e somente se existe ao menos um work log não
 * excluído. Quando {@code 008} publicar {@code WorkLogService.countByTicket}, a contagem real
 * substitui esta derivação neste ponto único — e as guardas não mudam.
 */
@Component
public class TicketWorkLogGate {

    /** RN-305, RN-307: o ticket possui horas apuradas. */
    public boolean hasWorkLogs(Ticket ticket) {
        return ticket.getSpentMinutes() > 0;
    }
}
