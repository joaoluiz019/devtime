package com.devtime.ticket;

import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketExceptions;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cronômetro ativo apontando para o ticket (RN-311).
 *
 * <p>Concluir com o cronômetro rodando produziria tempo registrado <b>depois</b> da conclusão —
 * horas órfãs em um ticket já entregue e possivelmente faturado. {@code PAUSED} conta como ativo
 * (CE-ME-01, CX-11): o trabalho não terminou, apenas parou.
 *
 * <p>A consulta chega por {@link ActiveTimerSource}, interface declarada aqui e implementada por
 * {@code 009-timer}. A inversão é necessária: {@code timer} já depende de {@code ticket}, e
 * consultar {@code TimerQueryService} daqui fecharia o ciclo que AR-09 proíbe. A lista de fontes
 * pode estar vazia — e nesse caso nenhuma conclusão é bloqueada, que é a resposta correta quando a
 * feature de cronômetro não está presente.
 */
@Component
@RequiredArgsConstructor
public class ActiveTimerGuard {

    private final List<ActiveTimerSource> timerSources;

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2311} / {@code 409}
     *     quando houver cronômetro {@code RUNNING} ou {@code PAUSED} no ticket
     */
    public void assertNoActiveTimer(Ticket ticket) {
        List<UUID> activeTimers = activeTimersOf(ticket);
        if (!activeTimers.isEmpty()) {
            throw TicketExceptions.activeTimer(activeTimers);
        }
    }

    /** Cronômetros {@code RUNNING} ou {@code PAUSED} do ticket, de todas as fontes registradas. */
    protected List<UUID> activeTimersOf(Ticket ticket) {
        return timerSources.stream()
                .flatMap(source -> source.activeTimerIdsOf(ticket.getId()).stream())
                .distinct()
                .toList();
    }
}
