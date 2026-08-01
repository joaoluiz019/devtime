package com.devtime.contract;

import com.devtime.contract.dto.BalanceResponses.PeriodStatementResponse;
import java.util.UUID;

/**
 * Extrato explicativo do período (§10 de contracts.md).
 *
 * <p>Um saldo que o cliente não consegue conferir é tão ruim quanto um saldo errado: ele precisa
 * ver de onde vieram as horas contratadas, o que foi transportado, quais ajustes existiram e quais
 * registros consumiram o quê. Um número sem rastro gera disputa.
 */
public interface PeriodStatementService {

    /**
     * Extrato em ordem cronológica, com saldo acumulado após cada movimento.
     *
     * <p>As entradas de crédito — contratado e transportado — vêm primeiro porque são a origem do
     * saldo; os ajustes e os registros de horas seguem pela data em que ocorreram. O acumulado é o
     * que permite ao cliente apontar exatamente onde o saldo virou negativo.
     */
    PeriodStatementResponse statement(UUID periodId);
}
