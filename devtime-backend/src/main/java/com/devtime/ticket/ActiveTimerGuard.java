package com.devtime.ticket;

import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketExceptions;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Cronômetro ativo apontando para o ticket (RN-311).
 *
 * <p>Concluir com o cronômetro rodando produziria tempo registrado <b>depois</b> da conclusão —
 * horas órfãs em um ticket já entregue e possivelmente faturado. {@code PAUSED} conta como ativo
 * (CE-ME-01, CX-11): o trabalho não terminou, apenas parou.
 *
 * <p><b>Estado nesta sprint:</b> {@code 009-timer} não foi implementada e a tabela {@code timers}
 * não existe (database.md §8.1, V015). Sem cronômetros persistidos, {@link #activeTimersOf} devolve
 * lista vazia e nenhuma conclusão é bloqueada. A classe existe agora, e não depois, porque é o
 * ponto único onde RN-311 é aplicada: quando {@code 009} publicar {@code TimerService}, a consulta
 * entra aqui e toda conclusão já passa por este caminho. A pendência está registrada no {@code
 * CHANGELOG.md}.
 */
@Component
public class ActiveTimerGuard {

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

    /**
     * Cronômetros {@code RUNNING} ou {@code PAUSED} do ticket.
     *
     * <p>Ponto de extensão de {@code 009-timer}: passará a consultar {@code TimerService}. Enquanto
     * a feature não existe, a resposta é sempre vazia — e é essa a resposta correta, porque nenhum
     * cronômetro pode existir sem a tabela que os persiste.
     */
    protected List<UUID> activeTimersOf(Ticket ticket) {
        return List.of();
    }
}
