package com.devtime.ticket;

import com.devtime.ticket.domain.Ticket;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Existência de horas registradas no ticket, consultada por RN-305 e RN-307.
 *
 * <p>Com {@code 008-worklogs} entregue, a contagem é <b>real</b> e chega por {@link
 * TicketWorkLogCountSource}. Até aqui era derivada de {@code spentMinutes}: a derivação era exata —
 * RN-115 exige {@code netMinutes > 0} e RN-308 mantém {@code spentMinutes} como a soma desses
 * valores —, mas dependia de essa cadeia permanecer verdadeira. A contagem direta não depende de
 * nada.
 *
 * <p>A inversão é obrigatória, não estilística: {@code worklog} depende de {@code ticket} por
 * RN-101, e injetar {@code WorkLogService} aqui fecharia um ciclo entre features (AR-09, BR-008).
 */
@Component
@RequiredArgsConstructor
public class TicketWorkLogGate {

    private final List<TicketWorkLogCountSource> workLogSources;

    /** RN-305, RN-307: o ticket possui horas apuradas. */
    public boolean hasWorkLogs(Ticket ticket) {
        return workLogSources.stream()
                        .mapToLong(source -> source.countByTicket(ticket.getId()))
                        .sum()
                > 0;
    }
}
