package com.devtime.ticket;

import com.devtime.audit.AuditService;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.ContractResponses.ContractRefResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.error.OwnershipViolationException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketExceptions;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.dto.TicketRequests.TicketAssignRequest;
import com.devtime.ticket.dto.TicketRequests.TicketTransitionRequest;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import com.devtime.ticket.event.TicketEvents.TicketAssignedEvent;
import com.devtime.ticket.event.TicketEvents.TicketReopenedEvent;
import com.devtime.ticket.event.TicketEvents.TicketStatusChangedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transições e atribuição (spec 007 §11).
 *
 * <p>BR-072: <b>toda guarda é verificada antes de qualquer efeito</b>. A ordem é: ownership,
 * versão, matriz de transição, guardas específicas do destino e só então a mudança de estado.
 * Inverter produziria um ticket parcialmente transicionado quando a última guarda falhasse.
 *
 * <p>§28 da spec: {@code blockReason} <b>não</b> entra em log — é texto livre, como título e
 * descrição.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TicketTransitionServiceImpl implements TicketTransitionService {

    private static final String ENTITY_TYPE = "Ticket";

    private final TicketRepository repository;
    private final TicketStateMachine stateMachine;
    private final TicketKeyBuilder keyBuilder;
    private final ActiveTimerGuard activeTimerGuard;
    private final BlockReasonValidator blockReasonValidator;
    private final AssigneeValidator assigneeValidator;
    private final SystemCommentEmitter systemCommentEmitter;
    private final ContractService contractService;
    private final TicketService ticketService;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TICKET_TRANSITION')")
    public TicketResponse transition(UUID id, TicketTransitionRequest request) {
        Ticket ticket = require(id);
        assertOwnershipForMember(ticket); // nota ⁴ de permissions.md §7
        assertVersion(ticket, request.version()); // RN-004

        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.targetStatus();

        // ME-03: auto-transição é ignorada silenciosamente, sem efeito e sem auditoria (CX-16).
        if (from == to) {
            return ticketService.getById(id);
        }

        stateMachine.assertCanTransition(from, to); // ME-04 — DEVTIME-2010

        String blockReason = null;
        if (to == TicketStatus.BLOCKED) {
            blockReason = blockReasonValidator.requireReason(request.blockReason()); // §4.7
        }
        if (to == TicketStatus.DONE) {
            activeTimerGuard.assertNoActiveTimer(ticket); // RN-311
        }
        if (to == TicketStatus.BACKLOG && from == TicketStatus.CANCELLED) {
            assertContractAllowsReactivation(ticket); // §4.7 — CX-15
        }

        applyTransition(ticket, to, blockReason);

        String key = keyOf(ticket);
        auditService.record(
                "TICKET_STATUS_CHANGED",
                ENTITY_TYPE,
                id,
                Map.of("status", from.name()),
                Map.of("status", to.name()),
                // §18: blockReason vai para o metadata da trilha (dado do tenant), nunca para log.
                blockReason == null ? Map.of() : Map.of("blockReason", blockReason));

        TicketStatusChangedEvent event =
                new TicketStatusChangedEvent(
                        id,
                        key,
                        from.name(),
                        to.name(),
                        blockReason,
                        tenantContext.requireUserId(),
                        false);
        systemCommentEmitter.emit(event); // RN-815

        log.info("transição de ticket ticketId={} key={} from={} to={}", id, key, from, to);
        return ticketService.getById(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TICKET_ASSIGN')")
    public TicketResponse assign(UUID id, TicketAssignRequest request) {
        Ticket ticket = require(id);
        assertOwnershipForMember(ticket); // nota ⁴
        assertVersion(ticket, request.version()); // RN-004
        assigneeValidator.assertAssignable(request.assigneeId()); // RN-304

        UUID previous = ticket.getAssigneeId();
        if (java.util.Objects.equals(previous, request.assigneeId())) {
            return ticketService.getById(id);
        }
        ticket.setAssigneeId(request.assigneeId());

        String key = keyOf(ticket);
        Map<String, Object> before = new HashMap<>();
        before.put("assigneeId", previous);
        Map<String, Object> after = new HashMap<>();
        after.put("assigneeId", request.assigneeId());
        auditService.record("TICKET_ASSIGNED", ENTITY_TYPE, id, before, after);

        // FA-04: notifica o novo responsável; o anterior não é notificado da remoção.
        systemCommentEmitter.emit(
                new TicketAssignedEvent(id, key, previous, request.assigneeId())); // RN-815

        log.info("responsável do ticket alterado ticketId={} key={}", id, key);
        return ticketService.getById(id);
    }

    /**
     * RN-312, executada dentro da transação do work log.
     *
     * <p>Sem {@code @PreAuthorize}: o ator é o sistema, não uma pessoa — quem registra as horas
     * pode não ter {@code TICKET_TRANSITION}, e exigir a permissão aqui impediria que um {@code
     * MEMBER} lançasse horas em um ticket concluído por outra pessoa (CE-P-08).
     */
    @Override
    @Transactional
    public void reopenOnWorkLog(UUID ticketId, UUID workLogId) {
        Ticket ticket = require(ticketId);
        if (ticket.getStatus() != TicketStatus.DONE) {
            return;
        }
        applyTransition(ticket, TicketStatus.IN_PROGRESS, null);

        String key = keyOf(ticket);
        auditService.recordSystemAction(
                "TICKET_STATUS_CHANGED",
                ENTITY_TYPE,
                ticketId,
                Map.of("status", TicketStatus.DONE.name()),
                Map.of("status", TicketStatus.IN_PROGRESS.name()),
                // §18: registrar qual work log disparou é o que evita que o responsável veja o
                // ticket voltar a "em andamento" sem explicação.
                Map.of("workLogId", workLogId));

        systemCommentEmitter.emit(
                new TicketStatusChangedEvent(
                        ticketId,
                        key,
                        TicketStatus.DONE.name(),
                        TicketStatus.IN_PROGRESS.name(),
                        null,
                        null,
                        true));
        // Notificação após o commit: entrega externa não pode reverter uma reabertura já decidida.
        events.publish(new TicketReopenedEvent(ticketId, key, workLogId));

        log.info("ticket reaberto por work log ticketId={} workLogId={}", ticketId, workLogId);
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    /**
     * Efeitos de entrada e de saída de cada estado (RN-310).
     *
     * <p>{@code startedAt} é preenchido apenas na <b>primeira</b> entrada em {@code IN_PROGRESS} e
     * nunca sobrescrito: ele responde "quando o trabalho começou", e reescrevê-lo a cada retomada
     * apagaria o histórico. {@code completedAt} é o oposto — preenchido em {@code DONE} e limpo em
     * <b>toda</b> saída, porque um ticket que voltou não está concluído (INV-TCK-04).
     */
    private void applyTransition(Ticket ticket, TicketStatus to, String blockReason) {
        if (to == TicketStatus.IN_PROGRESS && ticket.getStartedAt() == null) {
            ticket.setStartedAt(clock.now()); // RN-310
        }
        if (to == TicketStatus.DONE) {
            ticket.setCompletedAt(clock.now()); // RN-310
        } else if (ticket.getStatus() == TicketStatus.DONE) {
            ticket.setCompletedAt(null); // RN-310: toda saída de DONE limpa
        }
        if (to == TicketStatus.BLOCKED) {
            ticket.setBlockReason(blockReason);
        } else {
            // O motivo pertence ao impedimento; mantê-lo após o desbloqueio confundiria a leitura.
            ticket.setBlockReason(null);
        }
        // RN-314: cancelar não devolve horas nem exclui work logs — nada a fazer além do status.
        ticket.setStatus(to);
    }

    /** §4.7: reativar exige contrato {@code ACTIVE} ou {@code SUSPENDED} (CX-15). */
    private void assertContractAllowsReactivation(Ticket ticket) {
        // `acceptsWorkLogs` já traduz RN-306 (ACTIVE ou SUSPENDED) e chega decidido por 004: esta
        // feature não conhece ContractStatus, que é domínio de outra (AR-02).
        ContractRefResponse contract = contractService.getRefById(ticket.getContractId());
        if (!contract.acceptsWorkLogs()) {
            throw TicketExceptions.invalidTransition(
                    TicketStatus.CANCELLED, TicketStatus.BACKLOG, java.util.Set.of());
        }
    }

    /**
     * Nota ⁴ de permissions.md §7: {@code MEMBER} só transiciona e atribui tickets próprios.
     *
     * <p>A verificação é derivada da <b>permissão</b>, não do nome do papel: quem possui {@code
     * TICKET_UPDATE_ANY} (OWNER, ADMIN, MANAGER) dispensa ownership por OWN-08. Comparar papéis
     * literalmente quebraria em F6, quando papéis passarem a ser configuráveis.
     */
    private void assertOwnershipForMember(Ticket ticket) {
        if (tenantContext.currentPermissions().contains(Permission.TICKET_UPDATE_ANY)) {
            return;
        }
        UUID currentUserId = tenantContext.requireUserId();
        boolean own =
                currentUserId.equals(ticket.getReporterId())
                        || currentUserId.equals(ticket.getAssigneeId());
        if (!own) {
            throw new OwnershipViolationException(ENTITY_TYPE); // OWN-04 — DEVTIME-1103
        }
    }

    private Ticket require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> EntityNotFoundException.of(Ticket.class, id));
    }

    private void assertVersion(Ticket ticket, long expected) {
        if (ticket.getVersion() != null && ticket.getVersion() != expected) {
            throw BusinessRuleException.versionConflict(ENTITY_TYPE, expected); // RN-004
        }
    }

    private String keyOf(Ticket ticket) {
        return keyBuilder.build(
                contractService.getRefById(ticket.getContractId()).code(), ticket.getNumber());
    }
}
