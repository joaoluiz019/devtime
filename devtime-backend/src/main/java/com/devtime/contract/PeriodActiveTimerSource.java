package com.devtime.contract;

import java.util.List;
import java.util.UUID;

/**
 * Origem dos cronômetros ativos cujo trabalho pertenceria a um período (RN-240).
 *
 * <p>{@code timer} depende de {@code contract} — o início do cronômetro valida o contrato por
 * RN-306. Consultar {@code TimerQueryService} daqui fecharia o ciclo (AR-09), e resolver os tickets
 * do contrato exigiria que {@code contract} dependesse de {@code ticket}, que também depende dele.
 * A inversão resolve as duas coisas de uma vez: quem conhece cronômetros e tickets é {@code timer},
 * e é ele quem responde.
 *
 * <p>Sem implementação registrada, a lista é vazia e o fechamento não é bloqueado — correto quando
 * não existem cronômetros no sistema.
 */
public interface PeriodActiveTimerSource {

    /**
     * Cronômetros {@code RUNNING} ou {@code PAUSED} em tickets do contrato.
     *
     * <p>{@code PAUSED} conta (CE-ME-01, CX-18): o trabalho não terminou, apenas parou, e fechar o
     * período congelaria um ciclo que ainda vai receber a hora sendo contada neste instante.
     */
    List<UUID> activeTimerIdsForContract(UUID contractId);
}
