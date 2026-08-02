package com.devtime.tenant;

import java.util.UUID;

/**
 * Contratos que a remoção de membro precisa satisfazer em outras features (RN-458, RN-460).
 *
 * <p>Declarados aqui e implementados lá, no mesmo padrão de {@code TicketWorkLogCountSource}: a
 * direção de dependência já é {@code ticket → tenant}, {@code timer → tenant} e {@code worklog →
 * tenant}, e chamar aquelas features diretamente daqui fecharia ciclos (BR-008).
 *
 * <p>Por que não usar apenas eventos: §15 exige que o descarte do cronômetro e a reatribuição de
 * tickets ocorram <b>dentro</b> da transação — um membro sem acesso com cronômetro ativo produziria
 * um registro órfão sem autor válido — e {@code MemberRemovalResponse} (§23) precisa devolver as
 * contagens ao usuário. Um ouvinte de evento não devolve valor. O evento continua existindo para o
 * que é efeito colateral: notificação e revogação de sessões.
 */
public final class MemberRemovalPorts {

    private MemberRemovalPorts() {}

    /** Implementado por {@code 007-tickets}. */
    public interface TicketReassignmentSource {

        /**
         * FA-09: reatribui os tickets abertos do membro removido.
         *
         * @param toUserId novo responsável; nunca nulo — deixar tickets sem responsável esconderia
         *     trabalho em andamento
         * @return quantidade reatribuída
         */
        int reassignOpenTickets(UUID fromUserId, UUID toUserId);
    }

    /** Implementado por {@code 009-timer}. */
    public interface TimerDiscardSource {

        /**
         * RN-460 / CX-04: descarta o cronômetro ativo <b>ou pausado</b> do membro.
         *
         * @return quantidade descartada; zero é o caso comum
         */
        int discardTimersOf(UUID userId);
    }

    /** Implementado por {@code 008-worklogs}. */
    public interface WorkLogCountSource {

        /**
         * RN-458: quantos registros de horas <b>permanecem</b> após a remoção.
         *
         * <p>A contagem existe para ser devolvida ao usuário (§23): a transparência sobre o que foi
         * preservado é o que impede a leitura de que remover um membro apaga o trabalho dele.
         */
        long countByUser(UUID userId);
    }

    /** Implementado por {@code 004-contracts}. */
    public interface PeriodClosingStateSource {

        /** CX-12: existe período em {@code CLOSING} no tenant da sessão? */
        boolean hasPeriodInClosing();
    }
}
