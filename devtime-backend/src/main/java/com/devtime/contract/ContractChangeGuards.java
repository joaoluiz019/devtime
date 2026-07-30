package com.devtime.contract;

import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractExceptions;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.PeriodStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Guardas de alteração de contrato (RN-205, RN-207, RN-208; spec 004 §22.3).
 *
 * <p>Reúne {@code ContractDeletionGuard}, {@code MonthlyMinutesChangeGuard} e {@code
 * BillingDayChangeGuard}: as três verificam a mesma coisa sob ângulos diferentes — se a mudança
 * pretendida atinge dados já apurados.
 */
@Component
@RequiredArgsConstructor
public class ContractChangeGuards {

    private final ContractPeriodRepository periodRepository;

    /**
     * RN-205: a exclusão é permitida <b>apenas</b> em {@code DRAFT}.
     *
     * <p>contracts.md §8.6 é explícito: nos demais estados a resposta orienta o uso de {@code end}
     * ou {@code cancel}. Como um contrato em {@code DRAFT} não possui período nem registro de
     * horas, a guarda é integralmente verificável sem consultar {@code work_logs} — tabela que só
     * existe a partir de {@code 008}.
     */
    public void assertDeletable(Contract contract) {
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw ContractExceptions.deleteRestricted(contract.getStatus());
        }
    }

    /**
     * RN-207: alterar {@code monthlyMinutes} nunca atinge período fechado; o período aberto só muda
     * mediante confirmação explícita (CE-CT-02, CX-13, CX-14).
     */
    public void assertMonthlyMinutesChangeAllowed(Contract contract, boolean applyToCurrentPeriod) {
        List<ContractPeriod> closed =
                periodRepository.findByContractIdAndStatusIn(
                        contract.getId(), List.of(PeriodStatus.CLOSED, PeriodStatus.CLOSING));
        if (!closed.isEmpty() && applyToCurrentPeriod) {
            // ART-005: um relatório já emitido não muda porque o pacote foi renegociado hoje.
            throw ContractExceptions.changeAffectsClosedPeriod();
        }
        if (periodRepository.findOpenByContractId(contract.getId()).isPresent()
                && !applyToCurrentPeriod) {
            throw ContractExceptions.currentPeriodChangeNotConfirmed();
        }
    }

    /**
     * RN-208: o ciclo não pode mudar com horas lançadas no período aberto (CE-CT-03).
     *
     * <p>"Horas lançadas" é verificado por {@code consumedMinutes + nonBillableMinutes > 0}, campos
     * desnormalizados que o registro de horas alimenta na mesma transação (entities.md §9). Todo
     * work log incrementa um dos dois — faturável ou não —, então a soma ser zero equivale a não
     * haver registro no período. A contagem direta em {@code work_logs} substitui esta verificação
     * quando {@code 008} introduzir a tabela.
     */
    public void assertBillingDayChangeAllowed(Contract contract) {
        periodRepository
                .findOpenByContractId(contract.getId())
                .filter(period -> period.getConsumedMinutes() + period.getNonBillableMinutes() > 0)
                .ifPresent(
                        period -> {
                            throw ContractExceptions.billingDayLocked();
                        });
    }
}
