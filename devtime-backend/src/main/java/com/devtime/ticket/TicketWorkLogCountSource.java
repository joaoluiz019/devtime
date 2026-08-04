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

    /**
     * RN-308: totais reais por ticket, para a reconciliação noturna (spec 007 §22.4).
     *
     * <p>Em lote e não por ticket: o reconciliador percorre todos os tickets do sistema, e uma
     * consulta por ticket transformaria o job em dezenas de milhares de idas ao banco.
     *
     * <p>Tickets sem nenhum registro <b>não</b> aparecem no resultado. É deliberado: a ausência é
     * "zero", e materializar uma linha zerada por ticket sem horas gastaria memória proporcional ao
     * total de tickets em vez de ao total com horas.
     */
    java.util.Map<UUID, TicketWorkLogTotals> totalsByTicket();

    /**
     * @param spentMinutes soma de {@code netMinutes} de todos os registros do ticket
     * @param billableMinutes soma restrita aos registros faturáveis (INV-TCK-05)
     */
    record TicketWorkLogTotals(int spentMinutes, int billableMinutes) {}
}
