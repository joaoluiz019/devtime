package com.devtime.ticket;

import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.ticket.domain.Ticket;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atualização incremental de {@code spentMinutes} e {@code billableMinutes} (ver {@link
 * TicketTotalsService}).
 *
 * <p>Sem {@code @PreAuthorize}: é serviço interno, chamado de dentro da transação de {@code 008}
 * que já verificou {@code WORKLOG_CREATE}/{@code WORKLOG_UPDATE_*}. Não há rota HTTP que o alcance
 * — e é justamente essa ausência que impede a manipulação dos totais por API (SG-08).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TicketTotalsServiceImpl implements TicketTotalsService {

    private final TicketRepository repository;

    @Override
    @Transactional
    public void applyWorkLogDelta(UUID ticketId, int spentDelta, int billableDelta) {
        if (spentDelta == 0 && billableDelta == 0) {
            return;
        }
        // ART-024: ticket de outro tenant é inexistente; o UPDATE filtrado não afetaria linha
        // alguma e a operação falharia em silêncio se não verificássemos antes.
        Ticket ticket =
                repository
                        .findById(ticketId)
                        .orElseThrow(() -> EntityNotFoundException.of(Ticket.class, ticketId));

        // O UPDATE incremental delega a soma ao banco, onde a linha está travada pela escrita.
        // Ler-modificar-escrever perderia atualizações sob dois work logs simultâneos no mesmo
        // ticket — cenário comum quando duas pessoas registram horas no mesmo item.
        repository.adjustTotals(ticket.getId(), spentDelta, billableDelta);
        log.debug(
                "totais do ticket ajustados ticketId={} spentDelta={} billableDelta={}",
                ticketId,
                spentDelta,
                billableDelta);
    }
}
