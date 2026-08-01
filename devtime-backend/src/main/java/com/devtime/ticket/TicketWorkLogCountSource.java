package com.devtime.ticket;

import java.util.UUID;

/**
 * Origem da contagem de horas registradas em um ticket (RN-305, RN-307).
 *
 * <p>{@code worklog} depende de {@code ticket} — todo work log pertence a um ticket (RN-101).
 * Chamar {@code WorkLogService} a partir daqui criaria o ciclo que BR-008 e AR-09 proíbem. A
 * inversão mantém o grafo acíclico: {@code ticket} declara, {@code worklog} implementa — o mesmo
 * arranjo já usado por {@link com.devtime.contract.MemberContractLinkSource}.
 *
 * <p>Quando nenhuma implementação está presente, a contagem é zero e as guardas de RN-305/RN-307
 * ficam abertas — o que é correto, porque sem a feature de horas nenhum work log existe.
 */
public interface TicketWorkLogCountSource {

    /** Quantidade de registros de horas não excluídos do ticket. */
    long countByTicket(UUID ticketId);
}
