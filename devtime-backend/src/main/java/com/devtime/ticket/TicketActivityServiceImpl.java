package com.devtime.ticket;

import com.devtime.audit.AuditService;
import com.devtime.audit.dto.AuditEntry;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.dto.TicketResponses.TicketActivityEvent;
import com.devtime.ticket.dto.TicketResponses.TicketActivityResponse;
import com.devtime.user.UserService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Linha do tempo do ticket (tickets.md §9.1).
 *
 * <p>Une fontes heterogêneas em um tipo comum ordenado por instante. Nesta sprint as fontes são a
 * trilha de auditoria e os comentários; os <b>work logs</b> chegam com {@code 008-worklogs}.
 *
 * <p><b>Escopo de dados de {@code MEMBER}</b> (§9 de permissions.md, IMP-02): work logs de
 * terceiros são omitidos para quem não possui {@code WORKLOG_VIEW_ANY}. O filtro precisa ser
 * aplicado na consulta, nunca em memória — filtrar depois de carregar vaza informação por contagem
 * e por paginação. Enquanto {@code 008} não existe, não há work log a filtrar; o ponto de aplicação
 * está marcado para receber a condição junto com a fonte.
 *
 * <p>Dívida conhecida (OB-08 da spec): a união ocorre em tempo de consulta. Funciona até algumas
 * centenas de eventos por ticket. Materializar uma tabela de eventos duplicaria dado que já existe,
 * exigindo reconciliação, para um problema ainda não observado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketActivityServiceImpl implements TicketActivityService {

    private static final String ENTITY_TYPE = "Ticket";

    /** Máximo de eventos por página (§20 da spec). */
    private static final int MAX_PAGE_SIZE = 50;

    private final TicketRepository repository;
    private final AuditService auditService;

    /**
     * Fontes externas de evento (comentários hoje; work logs com {@code 008}).
     *
     * <p>Injetadas como lista para que acrescentar uma fonte não exija tocar nesta classe — e para
     * que a ausência de uma feature simplesmente não contribua eventos.
     */
    private final List<TicketActivitySource> activitySources;

    private final UserService userService;

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public TicketActivityResponse activity(UUID ticketId, Instant cursor, int size) {
        Ticket ticket =
                repository
                        .findById(ticketId)
                        .orElseThrow(() -> EntityNotFoundException.of(Ticket.class, ticketId));

        int pageSize = Math.min(size <= 0 ? MAX_PAGE_SIZE : size, MAX_PAGE_SIZE);

        List<TicketActivityEvent> merged =
                java.util.stream.Stream.concat(
                                auditService.findByEntity(ENTITY_TYPE, ticket.getId()).stream()
                                        .map(this::toEvent),
                                activitySources.stream()
                                        .flatMap(
                                                source ->
                                                        source.activityOf(ticket.getId()).stream()))
                        .filter(event -> cursor == null || event.occurredAt().isBefore(cursor))
                        .sorted(Comparator.comparing(TicketActivityEvent::occurredAt).reversed())
                        .toList();

        List<TicketActivityEvent> page = merged.stream().limit(pageSize).toList();
        boolean hasMore = merged.size() > page.size();
        Instant nextCursor = page.isEmpty() ? null : page.get(page.size() - 1).occurredAt();
        return new TicketActivityResponse(page, nextCursor, hasMore);
    }

    /**
     * Traduz um registro de auditoria em evento da linha do tempo.
     *
     * <p>O tipo exposto é o vocabulário de {@code tickets.md} §9.1 ({@code CREATED}, {@code
     * STATUS_CHANGED}, ...), não o nome interno da ação — a API não deve vazar a convenção de
     * nomenclatura da trilha.
     */
    private TicketActivityEvent toEvent(AuditEntry entry) {
        String type =
                switch (entry.action()) {
                    case "TICKET_CREATED" -> "CREATED";
                    case "TICKET_STATUS_CHANGED" -> "STATUS_CHANGED";
                    case "TICKET_ASSIGNED" -> "ASSIGNED";
                    case "TICKET_CONTRACT_MOVED" -> "CONTRACT_MOVED";
                    case "TICKET_TAGS_CHANGED" -> "TAGS_CHANGED";
                    case "TICKET_DELETED" -> "DELETED";
                    default -> "UPDATED";
                };
        return new TicketActivityEvent(
                type,
                entry.occurredAt(),
                entry.actorId() == null ? null : userService.summaryOf(entry.actorId()),
                Map.of("before", entry.beforeState(), "after", entry.afterState()));
    }
}
