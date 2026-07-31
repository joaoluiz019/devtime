package com.devtime.ticket;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Número sequencial do ticket dentro do contrato (RN-302, INV-TCK-01).
 *
 * <p><b>A atomicidade é requisito, não escolha</b> (§13.3 da spec 007). Sob duas criações
 * simultâneas no mesmo contrato, {@code MAX(number) + 1} calculado na aplicação leria o mesmo
 * máximo duas vezes e produziria chaves duplicadas — comunicadas ao mesmo cliente (CP-03, R-01). A
 * falha é silenciosa em ambiente de teste sequencial e só aparece em produção.
 *
 * <p><b>Como a serialização é obtida:</b> um lock consultivo de transação por contrato ({@code
 * pg_advisory_xact_lock}). É liberado automaticamente no commit ou no rollback, não exige linha
 * alguma para travar — o que importa quando o contrato ainda não possui nenhum ticket — e não
 * introduz schema novo.
 *
 * <p><b>Alternativas avaliadas e rejeitadas:</b>
 *
 * <ul>
 *   <li><i>{@code SELECT ... FOR UPDATE} sobre {@code tickets}</i>, como sugere database.md §7.7:
 *       não trava nada quando o contrato tem zero tickets, deixando o primeiro par de criações
 *       simultâneas em corrida.
 *   <li><i>Lock pessimista na linha de {@code contracts}</i>: exigiria que esta feature alcançasse
 *       o repositório de {@code 004}, proibido por BR-002.
 *   <li><i>Uma {@code SEQUENCE} por contrato</i>: seria um objeto de schema por linha de {@code
 *       contracts} — rejeitada em database.md §7.7.
 * </ul>
 *
 * <p>O índice único {@code uq_tickets_contract_number} permanece como barreira final: se o lock
 * falhar por qualquer motivo, o banco recusa a duplicata em vez de aceitá-la.
 */
@Component
@RequiredArgsConstructor
public class TicketNumberGenerator {

    /**
     * Espaço de nomes do lock consultivo.
     *
     * <p>{@code pg_advisory_xact_lock(int, int)} usa dois inteiros; o primeiro isola este uso de
     * qualquer outro lock consultivo do sistema, evitando que dois recursos diferentes com o mesmo
     * hash se bloqueiem mutuamente.
     */
    private static final int TICKET_NUMBER_LOCK_NAMESPACE = 7007;

    private final EntityManager entityManager;
    private final TicketRepository repository;

    /**
     * Reserva o próximo número do contrato.
     *
     * <p>Deve ser chamado dentro da transação de criação: o lock vive até o commit, e é isso que
     * garante que nenhuma outra transação leia o mesmo máximo no intervalo.
     *
     * @return o próximo número livre, começando em 1
     */
    public int nextFor(UUID contractId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(:namespace, hashtext(:key))")
                .setParameter("namespace", TICKET_NUMBER_LOCK_NAMESPACE)
                // BR-168: parâmetro vinculado, nunca concatenação de string em SQL.
                .setParameter("key", contractId.toString())
                .getSingleResult();
        return repository.findHighestNumber(contractId) + 1;
    }
}
