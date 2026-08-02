package com.devtime.ticket;

import com.devtime.audit.AuditService;
import com.devtime.tenant.MemberRemovalPorts.TicketReassignmentSource;
import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link TicketReassignmentSource} (FA-09 de {@code specs/002-users}).
 *
 * <p>Reatribui apenas os tickets <b>abertos</b>: um ticket já concluído registra quem o concluiu, e
 * reescrever isso falsificaria o histórico que ART-003 exige preservar.
 *
 * <p>Roda na transação da remoção (§15). A operação é uma atualização em lote por consulta, e não
 * um laço com um {@code save} por ticket: um membro com centenas de tickets abertos produziria
 * centenas de idas ao banco dentro da transação que trava o vínculo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberTicketReassignmentAdapter implements TicketReassignmentSource {

    /** §4.7 de state-machines.md: estados em que o ticket ainda demanda trabalho. */
    static final List<TicketStatus> OPEN_STATUSES =
            List.of(
                    TicketStatus.BACKLOG,
                    TicketStatus.TODO,
                    TicketStatus.IN_PROGRESS,
                    TicketStatus.BLOCKED,
                    TicketStatus.IN_REVIEW);

    private static final String ENTITY_TYPE = "TICKET";
    private static final String ACTION_REASSIGNED = "TICKET_REASSIGNED";

    private final TicketRepository repository;
    private final AuditService auditService;

    @Override
    @Transactional
    public int reassignOpenTickets(UUID fromUserId, UUID toUserId) {
        List<Ticket> open = repository.findByAssigneeAndStatusIn(fromUserId, OPEN_STATUSES);
        open.forEach(
                ticket -> {
                    ticket.setAssigneeId(toUserId);
                    // RN-006: cada reatribuição é auditada individualmente. Um registro agregado
                    // não responderia "por que este ticket mudou de responsável?", que é a pergunta
                    // que a linha do tempo do ticket precisa responder.
                    auditService.record(
                            ACTION_REASSIGNED,
                            ENTITY_TYPE,
                            ticket.getId(),
                            Map.of("assigneeId", String.valueOf(fromUserId)),
                            Map.of("assigneeId", String.valueOf(toUserId)),
                            Map.of("reason", "MEMBER_REMOVED"));
                });
        if (!open.isEmpty()) {
            log.warn("tickets reatribuídos por remoção de membro quantidade={}", open.size());
        }
        return open.size();
    }
}
