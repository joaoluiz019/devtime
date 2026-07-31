package com.devtime.ticket;

import com.devtime.shared.pagination.PageResponse;
import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.domain.TicketType;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketRequests.TicketMoveContractRequest;
import com.devtime.ticket.dto.TicketRequests.TicketUpdateRequest;
import com.devtime.ticket.dto.TicketResponses.TicketMoveContractResponse;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import com.devtime.ticket.dto.TicketResponses.TicketSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Interface pública da feature 007 (spec §22.2).
 *
 * <p>Os métodos consumidos por outras features são {@link #getForWorkLog(UUID)} e {@link
 * #getKeyById(UUID)}; os demais servem a API HTTP.
 */
public interface TicketService {

    /** tickets.md §6: listagem paginada com filtros compostos. Devolve projeção (BR-107). */
    PageResponse<TicketSummaryResponse> search(
            UUID contractId,
            UUID clientId,
            List<TicketStatus> statuses,
            List<TicketType> types,
            List<TicketPriority> priorities,
            UUID assigneeId,
            UUID reporterId,
            List<UUID> tagIds,
            String search,
            Boolean overEstimate,
            Pageable pageable);

    TicketResponse getById(UUID id);

    /** FA-15: busca pela chave legível, o identificador que circula fora do sistema. */
    TicketResponse getByKey(String key);

    /** §6.1 da spec: a ordem das validações é normativa. */
    TicketResponse create(TicketCreateRequest request);

    /** RN-011: {@code number}, chave e {@code reporterId} permanecem inalterados. */
    TicketResponse update(UUID id, TicketUpdateRequest request);

    /** RN-305: apenas sem work logs e dentro do mesmo cliente; a chave <b>não</b> muda. */
    TicketMoveContractResponse moveContract(UUID id, TicketMoveContractRequest request);

    /** RN-307: bloqueado quando existem horas; o caminho é cancelar (RN-314). */
    void delete(UUID id);

    /**
     * Ticket apto a receber registro de horas.
     *
     * <p>Interface pública para {@code 008-worklogs} e {@code 009-timer}: devolve o ticket com
     * contrato e cliente para RN-109 e falha quando o contrato não aceita registro (RN-306).
     */
    TicketResponse getForWorkLog(UUID ticketId);

    /**
     * Chave legível do ticket.
     *
     * <p>Interface pública para {@code 012-reports} e {@code 013-notifications}: o relatório e a
     * notificação exibem {@code CT-0001-42}, não um UUID.
     */
    String getKeyById(UUID ticketId);
}
