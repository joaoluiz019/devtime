package com.devtime.contract;

import com.devtime.contract.dto.BalanceResponses.OverageCheckResponse;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import java.util.UUID;

/**
 * Saldo do período — feature 011 (spec §22.2).
 *
 * <p>É a fonte única das fórmulas canônicas (RN-218 a RN-223). O painel, os relatórios e os
 * limiares de alerta consomem daqui; nenhum deles recalcula. Uma segunda implementação da fórmula é
 * exatamente o modo de falha que RP-03 descreve: dois números para o mesmo saldo, e o cliente
 * encontra o terceiro.
 */
public interface BalanceService {

    /**
     * Saldo completo do período.
     *
     * <p>Ao vivo em {@code OPEN} e {@code REOPENED}, marcado como <b>parcial</b> (RN-702). Um
     * número em evolução exibido sem essa marcação será lido como final.
     */
    PeriodBalanceResponse getBalance(UUID periodId);

    /**
     * Incremento transacional de {@code consumedMinutes} e {@code nonBillableMinutes}.
     *
     * <p>Interface pública para {@code 008-worklogs}, chamada <b>dentro</b> da transação do work
     * log: a resposta {@code 201} já devolve o saldo atualizado (§6.1 passo 26 de specs/008), e um
     * saldo desatualizado no exato momento do registro destruiria a confiança no número.
     *
     * <p>CP-15: incremento, nunca reagregação. Reagregar todos os work logs do período a cada
     * registro seria linear no volume, no caminho mais quente do sistema. A divergência eventual é
     * corrigida pela reconciliação noturna e, definitivamente, pelo passo 1 do fechamento.
     */
    void applyConsumptionDelta(UUID periodId, int billableDelta, int nonBillableDelta);

    /**
     * RN-231: o registro de {@code additionalBillableMinutes} ultrapassaria o saldo?
     *
     * <p>Interface pública para {@code 008-worklogs}. Não decide nem bloqueia — devolve o fato e a
     * política do contrato.
     */
    OverageCheckResponse checkOverage(UUID periodId, int additionalBillableMinutes);
}
