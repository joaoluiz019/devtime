package com.devtime.tenant;

import com.devtime.tenant.MemberRemovalPorts.TicketReassignmentSource;
import com.devtime.tenant.MemberRemovalPorts.TimerDiscardSource;
import com.devtime.tenant.MemberRemovalPorts.WorkLogCountSource;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Efeitos cruzados da remoção de membro (RN-458, RN-460, spec §22.3).
 *
 * <p>Tudo aqui roda <b>dentro</b> da transação da remoção, por exigência de §15: um membro sem
 * acesso com cronômetro ativo produziria, ao ser encerrado, um registro de horas sem autor válido.
 * O que fica fora da transação é apenas a notificação, que não pode desfazer a remoção se falhar
 * (BR-128).
 *
 * <p>O que esta classe <b>não</b> faz é tão importante quanto o que faz: registros de horas,
 * tickets e comentários são preservados integralmente (RN-458, CP-04). A única coisa que se perde é
 * o cronômetro em andamento, e mesmo ele fica registrado na trilha.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberRemovalOrchestrator {

    /**
     * Os três ports são resolvidos sob demanda, e não no construtor.
     *
     * <p>Necessário, não estilístico: {@code ticket} e {@code timer} dependem de {@code
     * MembershipService} para validar responsável e dono de cronômetro, e este orquestrador é
     * dependência daquele serviço. Injeção direta fecharia um ciclo de <b>criação de beans</b> —
     * {@code MembershipService → orquestrador → TimerService → TicketService → MembershipService} —
     * ainda que a dependência entre pacotes permaneça em uma única direção (AR-02 preservado, já
     * que as implementações vivem nas features donas do dado).
     */
    private final org.springframework.beans.factory.ObjectProvider<TicketReassignmentSource>
            ticketReassignment;

    private final org.springframework.beans.factory.ObjectProvider<TimerDiscardSource> timerDiscard;

    private final org.springframework.beans.factory.ObjectProvider<WorkLogCountSource> workLogCount;

    /**
     * @param reassignTo novo responsável pelos tickets abertos; users.md §7.4 usa o executor da
     *     remoção como padrão
     */
    public Outcome apply(UUID removedUserId, UUID reassignTo) {
        // RN-460 / CX-04: o cronômetro é descartado antes da reatribuição — um timer ativo em um
        // ticket que muda de responsável produziria horas atribuídas a quem não trabalhou.
        int discardedTimers = timerDiscard.getObject().discardTimersOf(removedUserId);
        int reassignedTickets =
                ticketReassignment.getObject().reassignOpenTickets(removedUserId, reassignTo);
        long preservedWorkLogs = workLogCount.getObject().countByUser(removedUserId);

        log.warn(
                "remoção de membro aplicada timersDescartados={} ticketsReatribuídos={}"
                        + " registrosPreservados={}",
                discardedTimers,
                reassignedTickets,
                preservedWorkLogs);
        return new Outcome(preservedWorkLogs, reassignedTickets, discardedTimers);
    }

    /**
     * RN-460 aplicado isoladamente, na suspensão.
     *
     * <p>Suspender não reatribui tickets nem conta registros: o vínculo pode voltar, e mexer na
     * atribuição de trabalho de quem talvez retorne amanhã produziria ruído. O cronômetro, esse,
     * precisa parar — um membro sem acesso não pode continuar acumulando tempo.
     */
    public int discardTimersOf(UUID userId) {
        return timerDiscard.getObject().discardTimersOf(userId);
    }

    /** Contagens devolvidas ao usuário em {@code MemberRemovalResponse} (§23). */
    public record Outcome(long preservedWorkLogs, int reassignedTickets, int discardedTimers) {}
}
