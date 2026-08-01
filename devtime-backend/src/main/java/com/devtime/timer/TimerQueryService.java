package com.devtime.timer;

import com.devtime.timer.dto.TimerResponses.ActiveTimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas de cronômetro (spec 009 §22.2).
 *
 * <p>Separada de {@code TimerService} porque é consumida por outras features — {@code 007} para
 * RN-311 e {@code 011} para RN-240 — e mantê-la estreita evita que a interface de escrita do
 * cronômetro seja exposta a quem só precisa saber se existe um ativo.
 */
public interface TimerQueryService {

    /**
     * Cronômetro ativo do usuário autenticado, em qualquer tenant (RN-150).
     *
     * <p>{@code GET /timers/current} é chamado ao carregar <b>toda</b> tela, porque o componente de
     * cronômetro é global — daí a meta de p95 &lt; 100 ms e o índice único parcial que a sustenta.
     */
    Optional<TimerResponse> current();

    /** {@code TIMER_VIEW_ANY}: cronômetros ativos do tenant, sem histórico de pausas (§19.1). */
    List<ActiveTimerResponse> activeInTenant();

    /**
     * RN-311: existe cronômetro ativo no ticket?
     *
     * <p>Interface pública para {@code 007-tickets}. {@code PAUSED} conta como ativo (CE-ME-01,
     * CX-11): o trabalho não terminou, apenas parou, e concluir o ticket produziria tempo
     * registrado <b>depois</b> da conclusão.
     */
    boolean hasActiveForTicket(UUID ticketId);

    /** RN-311: identificadores dos cronômetros ativos do ticket, exibidos no corpo do erro. */
    List<UUID> activeTimerIdsForTicket(UUID ticketId);

    /**
     * RN-240: existe cronômetro ativo cujo trabalho pertenceria ao período?
     *
     * <p>Interface pública para {@code 011-bank-hours}. Fechar com um cronômetro rodando congelaria
     * um período que ainda vai receber horas.
     *
     * @param ticketIds tickets dos contratos do período, resolvidos pelo chamador — {@code timers}
     *     não desnormaliza o contrato e AR-02 impede a consulta cruzada
     */
    List<UUID> activeTimerIdsForTickets(List<UUID> ticketIds);
}
