package com.devtime.ticket;

import java.util.List;
import java.util.UUID;

/**
 * Origem dos cronômetros ativos de um ticket (RN-311).
 *
 * <p>{@code timer} depende de {@code ticket} — todo cronômetro aponta para um ticket. Consultar
 * {@code TimerQueryService} a partir daqui criaria o ciclo que BR-008 e AR-09 proíbem; a inversão o
 * evita, com {@code ticket} declarando e {@code timer} implementando.
 *
 * <p>Sem implementação registrada, a lista é vazia e nenhuma conclusão é bloqueada — a resposta
 * correta quando não existem cronômetros no sistema.
 */
public interface ActiveTimerSource {

    /**
     * Cronômetros {@code RUNNING} ou {@code PAUSED} do ticket.
     *
     * <p>Devolve identificadores, e não um booleano, porque a resposta de erro os inclui: saber que
     * <i>existe</i> um cronômetro sem poder chegar até ele não resolve nada para quem tenta
     * concluir o ticket.
     */
    List<UUID> activeTimerIdsOf(UUID ticketId);
}
