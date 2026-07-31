package com.devtime.ticket;

import com.devtime.shared.security.Permission;
import com.devtime.ticket.domain.TicketExceptions;
import com.devtime.ticket.domain.TicketStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Matriz de transições do ticket (state-machines.md §4.7).
 *
 * <p>A matriz é declarada <b>uma única vez</b> aqui. Duplicá-la no serviço ou na resposta
 * produziria divergência silenciosa entre o que a UI oferece e o que o backend aceita — e é
 * justamente uma transição proibida executada por engano que corrompe o histórico ({@code DONE →
 * CANCELLED}).
 *
 * <p>São 49 células: 22 transições válidas e 27 proibidas. As proibições relevantes e seus motivos:
 *
 * <ul>
 *   <li>{@code DONE → CANCELLED}: um ticket concluído representa trabalho entregue e horas
 *       possivelmente faturadas; cancelá-lo sugeriria que o trabalho não ocorreu.
 *   <li>{@code CANCELLED → *} exceto {@code BACKLOG}: reativar deve recomeçar o fluxo; ir direto a
 *       {@code IN_PROGRESS} pularia a repriorização, que é a decisão que justifica a reativação.
 *   <li>{@code IN_PROGRESS → BACKLOG}: o trabalho já começou e {@code startedAt} está preenchido;
 *       voltar produziria um ticket "não priorizado" com horas registradas.
 *   <li>{@code BLOCKED → TODO}/{@code IN_REVIEW}/{@code DONE}: o desbloqueio passa por {@code
 *       IN_PROGRESS}, tornando explícito que o trabalho foi retomado antes de avançar.
 *   <li>{@code BACKLOG}/{@code TODO} → {@code BLOCKED}: não se bloqueia o que não começou.
 * </ul>
 *
 * <p>As guardas que dependem de dados (cronômetro ativo, motivo do impedimento, situação do
 * contrato) pertencem ao serviço: esta classe deliberadamente não acessa nada.
 */
@Component
public class TicketStateMachine {

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = buildMatrix();

    public boolean canTransition(TicketStatus from, TicketStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** ME-06: alimenta {@code availableTransitions} para não oferecer o que seria rejeitado. */
    public Set<TicketStatus> availableTransitions(TicketStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    /**
     * Transições disponíveis conforme o estado <b>e</b> o papel (ME-06).
     *
     * <p>Sem {@code TICKET_TRANSITION} a lista é vazia: oferecer ação que resultaria em {@code 403}
     * é pior que não oferecê-la. A restrição adicional de {@code MEDIUM} — {@code MEMBER} só
     * transiciona tickets próprios (OWN-04, nota ⁴) — depende do ticket concreto e é aplicada pelo
     * serviço, não aqui.
     */
    public Set<TicketStatus> availableTransitions(TicketStatus from, Set<Permission> permissions) {
        if (!permissions.contains(Permission.TICKET_TRANSITION)) {
            return Set.of();
        }
        return availableTransitions(from);
    }

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2010} / {@code 409},
     *     com {@code availableTransitions} no corpo (EX-09)
     */
    public void assertCanTransition(TicketStatus from, TicketStatus to) {
        if (!canTransition(from, to)) {
            throw TicketExceptions.invalidTransition(from, to, availableTransitions(from));
        }
    }

    private static Map<TicketStatus, Set<TicketStatus>> buildMatrix() {
        Map<TicketStatus, Set<TicketStatus>> matrix = new EnumMap<>(TicketStatus.class);
        matrix.put(
                TicketStatus.BACKLOG,
                Set.of(TicketStatus.TODO, TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        matrix.put(
                TicketStatus.TODO,
                Set.of(TicketStatus.BACKLOG, TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        matrix.put(
                TicketStatus.IN_PROGRESS,
                Set.of(
                        TicketStatus.TODO,
                        TicketStatus.BLOCKED,
                        TicketStatus.IN_REVIEW,
                        TicketStatus.DONE,
                        TicketStatus.CANCELLED));
        matrix.put(TicketStatus.BLOCKED, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        matrix.put(
                TicketStatus.IN_REVIEW,
                Set.of(TicketStatus.IN_PROGRESS, TicketStatus.DONE, TicketStatus.CANCELLED));
        // DONE → CANCELLED é proibido; DONE → IN_REVIEW é permitido (devolução para revisão).
        matrix.put(TicketStatus.DONE, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.IN_REVIEW));
        matrix.put(TicketStatus.CANCELLED, Set.of(TicketStatus.BACKLOG));
        return Map.copyOf(matrix);
    }
}
