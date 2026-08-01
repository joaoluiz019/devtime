package com.devtime.contract;

import com.devtime.contract.domain.BalanceExceptions;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.shared.time.TenantClock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Guardas do fechamento de período (RN-239, RN-240).
 *
 * <p>Verificadas <b>antes</b> de qualquer efeito (BR-072), inclusive antes de marcar o período como
 * {@code CLOSING}: uma guarda que falhasse depois deixaria o período travado para escrita sem que o
 * fechamento tivesse ocorrido.
 */
@Component
@RequiredArgsConstructor
public class ClosingGuard {

    private final List<PeriodActiveTimerSource> timerSources;
    private final TenantClock clock;

    /**
     * RN-239: o fechamento só é permitido após o {@code endDate}, ou antecipadamente com
     * confirmação explícita de {@code ADMIN}/{@code OWNER}.
     *
     * <p>A confirmação existe porque fechar antes do fim congela um período que <b>ainda vai
     * receber horas</b>: elas passariam a exigir reabertura. É uma decisão legítima — encerrar um
     * contrato no meio do mês, por exemplo —, mas nunca acidental.
     */
    public void assertClosable(ContractPeriod period, boolean confirmed) {
        if (period.getStatus() != PeriodStatus.OPEN
                && period.getStatus() != PeriodStatus.REOPENED) {
            throw BalanceExceptions.invalidPeriodTransition(period.getStatus(), "CLOSE"); // ME-04
        }
        boolean afterEndDate = clock.today().isAfter(period.getEndDate());
        if (!afterEndDate && !confirmed) {
            throw BalanceExceptions.closeTooEarly(period.getEndDate());
        }
    }

    /**
     * RN-240: nenhum cronômetro ativo cujo trabalho pertenceria ao período.
     *
     * <p>{@code PAUSED} conta (CE-ME-01, CX-18): o trabalho não terminou. Fechar com um cronômetro
     * rodando congelaria um período que ainda vai receber a hora que está sendo contada neste
     * instante — e a correção exigiria reabertura, uma operação auditada que altera um relatório já
     * entregue.
     *
     * <p>A consulta chega por {@link PeriodActiveTimerSource}, declarada aqui e implementada por
     * {@code 009-timer}: {@code timer} já depende de {@code contract} e de {@code ticket}, e
     * consultá-los daqui fecharia dois ciclos (AR-09). Sem fonte registrada a lista é vazia e o
     * fechamento não é bloqueado — correto quando não existem cronômetros no sistema.
     */
    public void assertNoActiveTimer(ContractPeriod period) {
        List<UUID> activeTimers =
                timerSources.stream()
                        .flatMap(
                                source ->
                                        source
                                                .activeTimerIdsForContract(period.getContractId())
                                                .stream())
                        .distinct()
                        .toList();
        if (!activeTimers.isEmpty()) {
            throw BalanceExceptions.periodHasActiveTimer(activeTimers);
        }
    }
}
