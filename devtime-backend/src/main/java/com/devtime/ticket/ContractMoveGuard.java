package com.devtime.ticket;

import com.devtime.contract.dto.ContractResponses.ContractRefResponse;
import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketExceptions;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Movimentação de ticket entre contratos (RN-305, INV-TCK-02).
 *
 * <p>Duas guardas, ambas obrigatórias:
 *
 * <ol>
 *   <li><b>Nenhum work log.</b> As horas já apuradas pertencem ao saldo do contrato atual; movê-las
 *       realocaria consumo entre períodos já fechados.
 *   <li><b>Mesmo cliente.</b> O work log copia {@code clientId} na criação e nunca o altera
 *       (RN-109); mudar o cliente do ticket tornaria o histórico incoerente.
 * </ol>
 *
 * <p>A movimentação bem-sucedida <b>não</b> altera {@code number} nem a chave legível (RN-011,
 * CP-06): a chave já circulou em e-mail, reunião e possivelmente nota fiscal, e alterá-la quebraria
 * a única referência estável que existe. RN-305 mitiga o estranhamento restringindo a movimentação
 * a tickets sem horas — ou seja, recém-criados, cuja chave dificilmente circulou.
 */
@Component
@RequiredArgsConstructor
public class ContractMoveGuard {

    private final TicketWorkLogGate workLogGate;

    /**
     * @param currentContract contrato atual do ticket
     * @param targetContract contrato de destino, já validado como apto a receber registros (RN-306)
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2305} / {@code 409} com
     *     horas; {@code DEVTIME-2315} / {@code 422} entre clientes
     */
    public void assertMovable(
            Ticket ticket,
            ContractRefResponse currentContract,
            ContractRefResponse targetContract) {
        if (workLogGate.hasWorkLogs(ticket)) {
            throw TicketExceptions.contractMoveHasWorkLogs(ticket.getSpentMinutes()); // RN-305
        }
        UUID currentClientId =
                currentContract.client() == null ? null : currentContract.client().id();
        UUID targetClientId = targetContract.client() == null ? null : targetContract.client().id();
        if (currentClientId == null || !currentClientId.equals(targetClientId)) {
            throw TicketExceptions.contractMoveAcrossClients(); // RN-305
        }
    }
}
