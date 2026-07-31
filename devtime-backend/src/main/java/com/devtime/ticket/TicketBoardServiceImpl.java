package com.devtime.ticket;

import com.devtime.contract.ContractService;
import com.devtime.tag.TagLinkService;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.dto.TicketResponses.TicketBoardColumn;
import com.devtime.ticket.dto.TicketResponses.TicketBoardResponse;
import com.devtime.ticket.dto.TicketResponses.TicketSummaryResponse;
import com.devtime.user.UserService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quadro por situação (tickets.md §6.1).
 *
 * <p><b>Uma</b> consulta agrupada, nunca uma por coluna (CP-14): com sete situações, a
 * implementação ingênua custaria sete vezes mais em cada abertura do quadro — que é a tela mais
 * visitada da feature.
 *
 * <p>O limite de 50 cartões por coluna é do servidor. Um quadro que carrega cem mil cartões trava o
 * navegador antes de trafegar; {@code totalCount} devolve o total <b>real</b> para que a interface
 * indique que há mais itens em vez de mentir sobre o tamanho da coluna.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketBoardServiceImpl implements TicketBoardService {

    /** tickets.md §6.1: máximo de cartões devolvidos por coluna. */
    private static final int MAX_TICKETS_PER_COLUMN = 50;

    private final TicketRepository repository;
    private final TicketMapper mapper;
    private final TicketKeyBuilder keyBuilder;
    private final TagLinkService tagLinkService;
    private final ContractService contractService;
    private final UserService userService;

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public TicketBoardResponse board(UUID contractId, UUID assigneeId) {
        List<Ticket> tickets = repository.findBoardGrouped(contractId, assigneeId);

        Map<UUID, List<TagOptionResponse>> tags =
                tagLinkService.findByTicketIds(tickets.stream().map(Ticket::getId).toList());
        Map<UUID, String> contractCodes = contractCodesOf(tickets);

        Map<TicketStatus, List<Ticket>> byStatus = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            byStatus.put(status, new ArrayList<>());
        }
        tickets.forEach(ticket -> byStatus.get(ticket.getStatus()).add(ticket));

        List<TicketBoardColumn> columns = new ArrayList<>();
        byStatus.forEach(
                (status, columnTickets) ->
                        columns.add(
                                new TicketBoardColumn(
                                        status,
                                        columnTickets.size(),
                                        columnTickets.stream()
                                                .mapToInt(Ticket::getSpentMinutes)
                                                .sum(),
                                        columnTickets.stream()
                                                .limit(MAX_TICKETS_PER_COLUMN)
                                                .map(
                                                        ticket ->
                                                                toSummary(
                                                                        ticket,
                                                                        tags,
                                                                        contractCodes))
                                                .toList())));
        return new TicketBoardResponse(columns);
    }

    /** Códigos dos contratos presentes no quadro, uma consulta por contrato distinto. */
    private Map<UUID, String> contractCodesOf(List<Ticket> tickets) {
        Map<UUID, String> codes = new LinkedHashMap<>();
        tickets.forEach(
                ticket ->
                        codes.computeIfAbsent(
                                ticket.getContractId(),
                                id -> contractService.getRefById(id).code()));
        return codes;
    }

    private TicketSummaryResponse toSummary(
            Ticket ticket,
            Map<UUID, List<TagOptionResponse>> tags,
            Map<UUID, String> contractCodes) {
        String contractCode = contractCodes.getOrDefault(ticket.getContractId(), "");
        return mapper.toSummary(
                ticket,
                keyBuilder.build(contractCode, ticket.getNumber()),
                contractCode,
                userService.summaryOf(ticket.getAssigneeId()),
                tags.getOrDefault(ticket.getId(), List.of()));
    }
}
