package com.devtime.ticket.event;

import com.devtime.shared.event.DomainEvent;
import java.util.UUID;

/**
 * Eventos de domínio de {@code 007-tickets} (spec §15).
 *
 * <p>BR-180/BR-181: {@code record} imutável carregando <b>identificadores</b>, nunca entidades. Um
 * evento com a entidade dentro acopla o consumidor ao modelo do produtor e mantém viva uma
 * referência a um objeto gerenciado fora da sessão que o carregou.
 *
 * <p>Estão em {@code ticket.event} — e não em {@code ticket.domain} — porque {@code 014-comments}
 * precisa consumi-los para gerar os comentários de sistema de RN-815, e AR-02 proíbe alcançar o
 * pacote de domínio de outra feature. É o que evita o ciclo entre as duas features: {@code 007}
 * publica, {@code 014} ouve, e nenhuma conhece a implementação da outra.
 *
 * <p><b>Momento de publicação</b> (§15 da spec): comentário de sistema e totais desnormalizados são
 * consumidos <b>dentro</b> da transação, porque são parte do mesmo fato — um status alterado sem o
 * comentário correspondente deixa a linha do tempo incompleta. Notificações são consumidas <b>após
 * o commit</b>, porque envolvem entrega externa e uma falha de e-mail não pode reverter uma
 * transição já decidida (TX-06).
 */
public final class TicketEvents {

    private TicketEvents() {}

    /** Publicado na criação. Consumido por métricas. */
    public record TicketCreatedEvent(UUID ticketId, String ticketKey, UUID contractId)
            implements DomainEvent {}

    /**
     * Mudança de situação.
     *
     * <p>{@code from} e {@code to} são o <b>nome</b> da situação, não o enum: {@code TicketStatus}
     * pertence ao domínio desta feature, e AR-02 proíbe que os consumidores — {@code 014-comments}
     * hoje, {@code 013-notifications} depois — dependam dele. O evento é a fronteira, e uma
     * fronteira que exporta o enum acopla o consumidor ao modelo do produtor.
     *
     * @param actorId autor da transição; nulo quando o sistema é o ator (RN-312)
     * @param automatic transição disparada pelo sistema, não por uma pessoa
     */
    public record TicketStatusChangedEvent(
            UUID ticketId,
            String ticketKey,
            String from,
            String to,
            String blockReason,
            UUID actorId,
            boolean automatic)
            implements DomainEvent {}

    /**
     * Atribuição ou reatribuição de responsável.
     *
     * @param previousAssigneeId responsável anterior; nulo quando não havia
     * @param assigneeId novo responsável; nulo quando o responsável foi removido (FA-05)
     */
    public record TicketAssignedEvent(
            UUID ticketId, String ticketKey, UUID previousAssigneeId, UUID assigneeId)
            implements DomainEvent {}

    /** Movimentação entre contratos. A chave legível <b>não</b> muda (RN-011, CP-06). */
    public record TicketContractMovedEvent(
            UUID ticketId,
            String ticketKey,
            UUID previousContractId,
            UUID contractId,
            String previousContractCode,
            String contractCode)
            implements DomainEvent {}

    /**
     * RN-312: reabertura automática ao receber work log. Notifica o responsável após o commit.
     *
     * @param assigneeId responsável no momento da reabertura; nulo quando o ticket não tem um. Vai
     *     no evento para que o consumidor não precise reconsultar o ticket — BR-181 permite
     *     identificadores, e é justamente o identificador que o destinatário exige
     */
    public record TicketReopenedEvent(
            UUID ticketId, String ticketKey, UUID workLogId, UUID assigneeId)
            implements DomainEvent {}
}
