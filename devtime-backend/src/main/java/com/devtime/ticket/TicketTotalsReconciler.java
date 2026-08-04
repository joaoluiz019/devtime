package com.devtime.ticket;

import com.devtime.shared.maintenance.DenormalizationReconciler;
import com.devtime.ticket.TicketWorkLogCountSource.TicketWorkLogTotals;
import com.devtime.ticket.domain.Ticket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-308 / INV-TCK-05: recalcula {@code spentMinutes} e {@code billableMinutes} pela agregação real
 * (spec 007 §22.4).
 *
 * <p>Os dois totais são mantidos por incremento dentro da transação do work log, porque RN-308
 * dispara no caminho mais quente do sistema e reagregar ali seria linear no número de registros
 * (CP-12). Este job é a contrapartida: um incremento perdido deixa o ticket exibindo um total que
 * nunca mais se corrige, e o total do ticket é o que sustenta a comparação com {@code
 * estimatedMinutes}.
 *
 * <p>Ticket sem nenhum registro tem total zero, e a ausência na agregação é justamente isso — daí a
 * varredura ser sobre os tickets, e não sobre o resultado da agregação: um ticket cujos work logs
 * foram todos excluídos precisa voltar a zero, e ele não aparece no lado agregado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketTotalsReconciler implements DenormalizationReconciler {

    private static final TicketWorkLogTotals ZERO = new TicketWorkLogTotals(0, 0);

    private final TicketRepository repository;
    private final List<TicketWorkLogCountSource> workLogSources;

    @Override
    public String target() {
        return "ticket.spentMinutes";
    }

    @Override
    @Transactional
    public int reconcile() {
        Map<UUID, TicketWorkLogTotals> real = new HashMap<>();
        workLogSources.forEach(
                source ->
                        source.totalsByTicket()
                                .forEach(
                                        (ticketId, totals) ->
                                                real.merge(
                                                        ticketId,
                                                        totals,
                                                        TicketTotalsReconciler::sum)));

        int corrected = 0;
        for (Ticket ticket : repository.findAll()) {
            TicketWorkLogTotals totals = real.getOrDefault(ticket.getId(), ZERO);
            if (ticket.getSpentMinutes() != totals.spentMinutes()
                    || ticket.getBillableMinutes() != totals.billableMinutes()) {
                log.warn(
                        "totais de ticket divergentes ticketId={} spent={}->{} billable={}->{}",
                        ticket.getId(),
                        ticket.getSpentMinutes(),
                        totals.spentMinutes(),
                        ticket.getBillableMinutes(),
                        totals.billableMinutes());
                ticket.setSpentMinutes(totals.spentMinutes());
                ticket.setBillableMinutes(totals.billableMinutes());
                corrected++;
            }
        }
        return corrected;
    }

    private static TicketWorkLogTotals sum(TicketWorkLogTotals left, TicketWorkLogTotals right) {
        return new TicketWorkLogTotals(
                left.spentMinutes() + right.spentMinutes(),
                left.billableMinutes() + right.billableMinutes());
    }
}
