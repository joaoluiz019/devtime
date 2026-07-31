package com.devtime.ticket;

import com.devtime.ticket.dto.TicketResponses.TicketActivityEvent;
import java.util.List;
import java.util.UUID;

/**
 * Fonte externa de eventos para a linha do tempo do ticket (tickets.md §9.1).
 *
 * <p>A linha do tempo une auditoria, comentários e work logs. Comentário e work log pertencem a
 * outras features, e chamá-las diretamente daqui criaria o ciclo que BR-008 proíbe — {@code
 * 014-comments} já depende de {@code 007} para existir.
 *
 * <p>A inversão resolve: {@code 007} declara o contrato, as features que possuem eventos o
 * implementam, e o Spring injeta as implementações disponíveis. Uma feature ausente simplesmente
 * não contribui eventos, e a linha do tempo continua correta — foi assim que ela funcionou antes de
 * {@code 014} existir.
 */
public interface TicketActivitySource {

    /**
     * Eventos da fonte para um ticket, sem paginação.
     *
     * <p>A ordenação e o corte por cursor são do serviço de atividade, que precisa mesclar as
     * fontes antes de decidir a página.
     */
    List<TicketActivityEvent> activityOf(UUID ticketId);
}
