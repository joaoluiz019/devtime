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
     * Ticket apto a receber horas, na forma que {@code 008} e {@code 009} conseguem consumir.
     *
     * <p>{@link TicketResponse} expõe os enums de {@code ticket.domain}, que AR-02 proíbe às
     * features consumidoras. Esta referência traz apenas o que elas usam: os identificadores a
     * copiar para o work log (RN-109), a chave legível e a categoria padrão da cadeia de RN-104.
     *
     * <p>Aplica RN-306 como {@link #getForWorkLog(UUID)}: contrato encerrado responde {@code
     * DEVTIME-2306}.
     */
    com.devtime.ticket.dto.TicketResponses.TicketWorkLogRefResponse getRefForWorkLog(UUID ticketId);

    /**
     * A mesma referência, <b>sem</b> aplicar RN-306.
     *
     * <p>Existe para exibição, onde recusar é o comportamento errado: um cronômetro em execução
     * cujo contrato foi encerrado no meio do trabalho precisa continuar visível (CX-04, CX-22 de
     * {@code specs/009}). É o encerramento dele que falhará em RN-306, com a orientação de mover o
     * ticket — e para isso o usuário precisa antes conseguir ver o cronômetro.
     */
    com.devtime.ticket.dto.TicketResponses.TicketWorkLogRefResponse getRef(UUID ticketId);

    /**
     * Chave legível do ticket.
     *
     * <p>Interface pública para {@code 012-reports} e {@code 013-notifications}: o relatório e a
     * notificação exibem {@code CT-0001-42}, não um UUID.
     */
    String getKeyById(UUID ticketId);

    /**
     * Identificadores dos tickets de um contrato.
     *
     * <p>Interface pública para {@code 011-bank-hours}: RN-240 precisa saber se existe cronômetro
     * ativo em algum ticket do contrato antes de fechar o período, e {@code timers} não
     * desnormaliza o contrato. Resolver os tickets aqui mantém cada consulta dentro da feature dona
     * da sua tabela (AR-02).
     */
    List<UUID> findIdsByContract(UUID contractId);
}
