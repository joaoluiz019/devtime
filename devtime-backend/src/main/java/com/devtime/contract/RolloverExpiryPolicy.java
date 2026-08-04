package com.devtime.contract;

import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodBalance;
import org.springframework.stereotype.Component;

/**
 * Quanto do saldo transportado expira agora (RN-230, FA-14, CX-19, CX-20).
 *
 * <p><b>Lacuna de documentação, reportada e resolvida aqui.</b> RN-230 diz que o saldo transportado
 * "expira após {@code rolloverExpiryPeriods} períodos", mas nenhum documento define <b>como a idade
 * do saldo é rastreada</b>: {@code contract_periods} guarda {@code carriedInMinutes} e {@code
 * carriedOutMinutes}, e nada distingue, dentro de um saldo, o que veio do ciclo passado do que veio
 * de três ciclos atrás. Criar uma coluna para isso seria decidir modelo de dados, que IA-01 proíbe.
 *
 * <p>A leitura implementada é a única compatível com os campos que existem e com o exemplo de
 * FA-14: a concessão que entrou no período de sequência {@code S − N} vale por {@code N} períodos e
 * expira ao início do período corrente. O que ainda resta de saldo é o que se debita, limitado
 * <b>pela concessão</b> e <b>pelo transportado que de fato entrou</b> — nunca se debita mais saldo
 * do que existe (RN-237), e nunca se debita saldo contratado, que não expira.
 *
 * <p>Isolada em uma classe própria pelo mesmo motivo de {@code RolloverCalculator}: quando a regra
 * ganhar definição normativa, é aqui — e só aqui — que ela muda.
 */
@Component
public class RolloverExpiryPolicy {

    /** FA-14: justificativa normativa; é ela que identifica o ajuste automático no extrato. */
    public static final String JUSTIFICATION = "Expiração de saldo transportado";

    /**
     * Minutos a debitar do período corrente, ou zero quando nada expira.
     *
     * @param period período corrente, obrigatoriamente {@code OPEN} ou {@code REOPENED} (CX-19)
     * @param balance saldo já calculado do período corrente
     * @param grantMinutes {@code carriedInMinutes} do período que recebeu a concessão que expira,
     *     ou zero quando esse período não existe — contrato novo demais para haver expiração
     */
    public int expiringMinutes(ContractPeriod period, PeriodBalance balance, int grantMinutes) {
        if (grantMinutes <= 0 || period.getCarriedInMinutes() <= 0) {
            return 0;
        }
        // RN-220: `remaining` é negativo em excedente. Sem saldo restante nada há para expirar —
        // a concessão já foi consumida, e debitá-la de novo cobraria duas vezes pelo mesmo saldo.
        int remaining = Math.max(0, balance.remainingMinutes());
        return Math.min(grantMinutes, Math.min(period.getCarriedInMinutes(), remaining));
    }
}
