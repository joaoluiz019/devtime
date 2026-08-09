package com.devtime.timer;

import com.devtime.contract.PeriodActiveTimerSource;
import com.devtime.ticket.ActiveTimerSource;
import com.devtime.ticket.TicketService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contribuições de {@code 009-timer} às interfaces declaradas por outras features.
 *
 * <p>{@code timer} depende de {@code ticket} (o cronômetro aponta para um ticket) e de {@code
 * contract} (RN-306 na partida). Nenhuma das duas pode consultá-lo de volta sem fechar o ciclo que
 * AR-09 proíbe, então elas declaram o que precisam e esta feature implementa.
 *
 * <p>Nenhuma das duas fontes cria aresta nova no grafo: as dependências já existem.
 */
public final class TimerSourceAdapters {

    private TimerSourceAdapters() {}

    /** RN-311: conclusão de ticket bloqueada por cronômetro ativo, inclusive pausado. */
    @Component
    @RequiredArgsConstructor
    public static class TicketAdapter implements ActiveTimerSource {

        private final TimerRepository repository;

        @Override
        @Transactional(readOnly = true)
        public List<UUID> activeTimerIdsOf(UUID ticketId) {
            return repository.findActiveIdsForTicket(ticketId);
        }
    }

    /**
     * RN-240: fechamento de período bloqueado por cronômetro ativo.
     *
     * <p>A resolução dos tickets do contrato acontece <b>aqui</b>, e não em {@code contract}:
     * {@code timer} pode consultar {@code ticket}, mas {@code contract} não — {@code ticket} já
     * depende dele. Concentrar as duas consultas nesta fonte resolve as duas restrições de uma vez.
     */
    @Component
    @RequiredArgsConstructor
    public static class PeriodAdapter implements PeriodActiveTimerSource {

        private final TimerRepository repository;

        /**
         * Resolvido sob demanda, e não no construtor.
         *
         * <p>Necessário, não estilístico: {@code ContractServiceImpl} injeta a lista de {@code
         * PeriodActiveTimerSource}, e {@code TicketServiceImpl} depende de {@code ContractService}.
         * Injeção direta fecharia um ciclo de <b>criação de beans</b> — {@code ContractService →
         * PeriodAdapter → TicketService → ContractService} — ainda que a direção entre pacotes
         * permaneça a descrita acima (AR-02 preservado: a consulta continua sendo feita por {@code
         * timer} sobre a API pública de {@code ticket}).
         */
        private final org.springframework.beans.factory.ObjectProvider<TicketService> ticketService;

        @Override
        @Transactional(readOnly = true)
        public List<UUID> activeTimerIdsForContract(UUID contractId) {
            List<UUID> ticketIds = ticketService.getObject().findIdsByContract(contractId);
            if (ticketIds.isEmpty()) {
                return List.of();
            }
            return repository.findActiveIdsForTickets(ticketIds);
        }
    }
}
