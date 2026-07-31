package com.devtime.ticket;

import com.devtime.shared.event.DomainEvent;
import com.devtime.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Emissão dos fatos que geram comentário de sistema (RN-815).
 *
 * <p>RN-815 exige comentário automático em três gatilhos: mudança de situação, alteração de
 * responsável e alteração de contrato. A entidade {@code Comment} pertence a {@code 014-comments},
 * e BR-008 proíbe ciclo entre pacotes de feature — se {@code 007} chamasse {@code
 * SystemCommentService} diretamente, e {@code 014} referenciasse {@code Ticket}, o ciclo estaria
 * formado.
 *
 * <p>A solução é a prevista em §15 de ambas as specs: {@code 007} <b>publica o evento</b> e {@code
 * 014} o consome com {@code @EventListener} — <b>dentro</b> da mesma transação, porque a transição
 * e o comentário são o mesmo fato: um status alterado sem o comentário correspondente deixa a linha
 * do tempo incompleta.
 *
 * <p>Esta classe é intencionalmente fina. Ela existe como ponto único e nomeado de RN-815 para que
 * a regra tenha um lugar no código — sem ela, "onde RN-815 é aplicada?" não teria resposta, e o
 * gatilho ficaria diluído em três chamadas espalhadas pelo serviço de transição.
 */
@Component
@RequiredArgsConstructor
public class SystemCommentEmitter {

    private final DomainEventPublisher events;

    /**
     * Publica o fato que originará o comentário de sistema.
     *
     * @param event um dos três gatilhos de RN-815: {@code TicketStatusChangedEvent}, {@code
     *     TicketAssignedEvent} ou {@code TicketContractMovedEvent}
     */
    public void emit(DomainEvent event) {
        events.publish(event);
    }
}
