package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodBalance;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RN-230, CX-19 e CX-20: quanto do saldo transportado expira.
 *
 * <p>Teste puro, sem contexto Spring: a regra é aritmética e determinística (BR-150). O que ele
 * protege é a diferença entre debitar o saldo que sobrou e debitar duas vezes o saldo já consumido
 * — um erro que reduz silenciosamente o saldo que o cliente contratou.
 */
class RolloverExpiryPolicyTest {

    private final RolloverExpiryPolicy policy = new RolloverExpiryPolicy();

    @Test
    @DisplayName("RN-230: expira a concessão inteira quando o saldo restante a cobre")
    void expiresWholeGrantWhenBalanceCoversIt() {
        ContractPeriod period = period(600);

        assertThat(policy.expiringMinutes(period, balance(1200, 300), 600)).isEqualTo(600);
    }

    @Test
    @DisplayName("RN-237: nunca debita mais do que o saldo restante")
    void neverDebitsMoreThanRemaining() {
        ContractPeriod period = period(600);

        assertThat(policy.expiringMinutes(period, balance(1200, 900), 600))
                .as("restam 300 minutos; debitar 600 deixaria o disponível negativo")
                .isEqualTo(300);
    }

    @Test
    @DisplayName("RN-220: em excedente nada expira — a concessão já foi consumida")
    void nothingExpiresWhenOverconsumed() {
        ContractPeriod period = period(600);

        assertThat(policy.expiringMinutes(period, balance(1200, 1500), 600)).isZero();
    }

    @Test
    @DisplayName("Nunca debita mais do que o transportado que de fato entrou no período")
    void neverDebitsMoreThanCarriedIn() {
        ContractPeriod period = period(100);

        assertThat(policy.expiringMinutes(period, balance(1200, 0), 600))
                .as("saldo contratado não expira; só o transportado")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("CX-20: sem concessão a expirar, nada é debitado")
    void nothingExpiresWithoutGrant() {
        assertThat(policy.expiringMinutes(period(600), balance(1200, 0), 0)).isZero();
        assertThat(policy.expiringMinutes(period(0), balance(1200, 0), 600)).isZero();
    }

    private ContractPeriod period(int carriedInMinutes) {
        ContractPeriod period = new ContractPeriod();
        period.setCarriedInMinutes(carriedInMinutes);
        return period;
    }

    private PeriodBalance balance(int available, int consumed) {
        return new PeriodBalance(
                available, 0, 0, available, consumed, 0, available - consumed, 0, BigDecimal.ZERO);
    }
}
