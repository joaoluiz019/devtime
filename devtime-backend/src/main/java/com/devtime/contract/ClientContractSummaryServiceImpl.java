package com.devtime.contract;

import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.dto.ContractResponses.ClientContractSummaryResponse;
import com.devtime.contract.dto.ContractResponses.ContractHistoryPeriod;
import com.devtime.contract.dto.ContractResponses.ContractSummaryByContract;
import com.devtime.contract.dto.ContractResponses.SummaryTotals;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resumo consolidado do cliente (clients.md §8).
 *
 * <p>SM-02 prevê que períodos fechados sejam lidos do snapshot; snapshots pertencem a {@code
 * 011-bank-hours} e ainda não existem, então todos os períodos são lidos ao vivo. O agrupamento por
 * categoria depende de work logs ({@code 008}) e não é emitido — retorná-lo vazio seria lido como
 * "nenhuma hora nesta categoria" em vez de "informação indisponível".
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientContractSummaryServiceImpl implements ClientContractSummaryService {

    private final ContractRepository contractRepository;
    private final ContractPeriodRepository periodRepository;

    @Override
    @PreAuthorize("hasPermission(null, 'CLIENT_VIEW')")
    public ClientContractSummaryResponse summarize(UUID clientId, int periods) {
        List<Contract> contracts = contractRepository.findByClientId(clientId);

        List<ContractHistoryPeriod> history = new ArrayList<>();
        List<ContractSummaryByContract> byContract = new ArrayList<>();
        int contracted = 0;
        int consumed = 0;
        int nonBillable = 0;
        int overage = 0;

        for (Contract contract : contracts) {
            int contractMinutes = 0;
            for (ContractPeriod period :
                    periodRepository.findByContractIdOrderBySequence(contract.getId())) {
                int available =
                        period.getContractedMinutes()
                                + period.getCarriedInMinutes()
                                + period.getAdjustmentMinutes();
                int remaining = available - period.getConsumedMinutes();

                contracted += period.getContractedMinutes();
                consumed += period.getConsumedMinutes();
                nonBillable += period.getNonBillableMinutes();
                overage += Math.max(0, -remaining);
                contractMinutes += period.getConsumedMinutes();

                history.add(
                        new ContractHistoryPeriod(
                                period.getSequence(),
                                period.getLabel(),
                                period.getStatus(),
                                period.getContractedMinutes(),
                                period.getCarriedInMinutes(),
                                period.getAdjustmentMinutes(),
                                period.getConsumedMinutes(),
                                remaining,
                                Math.max(0, -remaining),
                                period.getCarriedOutMinutes()));
            }
            byContract.add(
                    new ContractSummaryByContract(
                            contract.getId(),
                            contract.getCode(),
                            contract.getName(),
                            contractMinutes));
        }

        // SM-03: histórico do mais antigo ao mais recente; a janela mantém os N mais recentes.
        history.sort(Comparator.comparing(ContractHistoryPeriod::label));
        List<ContractHistoryPeriod> window =
                history.size() <= periods
                        ? history
                        : history.subList(history.size() - periods, history.size());

        // CE-C-07: contratos em moedas diferentes não são convertidos; a moeda exibida é a do
        // primeiro contrato, e o agrupamento por moeda entra com os totais monetários de 011.
        String currency = contracts.isEmpty() ? null : contracts.get(0).getCurrency();

        return new ClientContractSummaryResponse(
                clientId,
                currency,
                new SummaryTotals(
                        contracted, consumed, nonBillable, contracted - consumed, overage),
                List.copyOf(window),
                List.copyOf(byContract));
    }
}
