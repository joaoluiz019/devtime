package com.devtime.dashboard;

import com.devtime.contract.dto.ContractResponses.ContractDashboardCard;
import com.devtime.dashboard.dto.DashboardResponses.ContractStatusDto;
import com.devtime.dashboard.dto.DashboardResponses.DashboardAlertDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alertas derivados do <b>estado atual</b> (CP-03, OB-02 de specs/010).
 *
 * <p>É a decisão menos óbvia da feature. Uma notificação é um evento passado e permanente — o
 * {@code dedupeKey} de RN-603 garante que ela não se repita. O painel responde a outra pergunta: "o
 * que está errado <b>agora</b>". Se um ajuste resolveu o excedente ontem, o alerta desaparece hoje,
 * mesmo com a notificação no histórico (CE-11, FA-13).
 *
 * <p>Derivá-lo das notificações produziria alertas fantasmas, que o usuário vê e não consegue
 * resolver porque a condição já não existe.
 */
public interface DashboardAlertService {

    /**
     * Alertas correspondentes à situação presente dos contratos exibidos.
     *
     * <p>Recebe os cartões já compostos em vez de consultar de novo: os números são os mesmos que a
     * tela exibe, e uma segunda leitura poderia divergir da primeira.
     *
     * @param contracts cartões já ordenados ou não, com severidade e saldo resolvidos
     * @param cardsById cartões de origem, para os limiares e a data de fim do contrato
     */
    List<DashboardAlertDto> deriveFrom(
            List<ContractStatusDto> contracts, Map<UUID, ContractDashboardCard> cardsById);
}
