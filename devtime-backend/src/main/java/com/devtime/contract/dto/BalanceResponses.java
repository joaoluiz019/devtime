package com.devtime.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs de saída do banco de horas — feature 011 (contracts.md §10 a §13). */
public final class BalanceResponses {

    private BalanceResponses() {}

    /**
     * Saldo do período (RN-218 a RN-223).
     *
     * <p>{@code isPartial} é verdadeiro em {@code OPEN} e {@code REOPENED} (RN-702) e é
     * <b>obrigatório</b> na exibição: um número em evolução apresentado sem essa marcação será lido
     * como final, e é assim que um saldo parcial vira uma cobrança contestada.
     *
     * @param consumptionRate percentual com 2 casas; nunca {@code double} — ponto flutuante binário
     *     produziria {@code 105.06999999} onde o cliente espera {@code 105,07}
     */
    @Schema(name = "PeriodBalanceResponse")
    public record PeriodBalanceResponse(
            UUID periodId,
            UUID contractId,
            int sequence,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            int contractedMinutes,
            int carriedInMinutes,
            int adjustmentMinutes,
            int availableMinutes,
            int consumedMinutes,
            int nonBillableMinutes,
            int remainingMinutes,
            int overageMinutes,
            BigDecimal consumptionRate,
            boolean isPartial,
            int reopenCount,
            String currency) {}

    /**
     * Resultado da consulta de excedente (RN-231 a RN-233), consumida por {@code 008-worklogs}.
     *
     * <p>Devolve o <b>fato</b> — o registro ultrapassaria o saldo? — junto com a política do
     * contrato, e deixa a decisão de bloquear, avisar ou permitir com quem está criando o work log.
     * Decidir aqui obrigaria esta feature a conhecer o fluxo de criação de {@code 008}; devolver só
     * a política obrigaria {@code 008} a refazer a aritmética do saldo, que é justamente o que
     * RP-03 proíbe duplicar.
     *
     * @param overagePolicy nome do valor de {@code OveragePolicy} do contrato
     * @param wouldExceed verdadeiro quando {@code consumed + adicional > available}
     * @param exceedingMinutes quanto passaria do disponível; zero quando não excede
     */
    @Schema(name = "OverageCheckResponse")
    public record OverageCheckResponse(
            UUID periodId,
            String overagePolicy,
            boolean wouldExceed,
            int availableMinutes,
            int consumedMinutes,
            int remainingMinutes,
            int exceedingMinutes) {}

    /**
     * Linha do extrato explicativo (§10 de contracts.md).
     *
     * <p>Um saldo que o cliente não consegue conferir é tão ruim quanto um saldo errado. O extrato
     * une work logs e ajustes em ordem cronológica, com o saldo acumulado após cada movimento.
     *
     * @param type {@code CONTRACTED}, {@code CARRIED_IN}, {@code ADJUSTMENT} ou {@code WORK_LOG}
     * @param minutes positivo credita saldo, negativo consome
     */
    @Schema(name = "PeriodStatementEntry")
    public record PeriodStatementEntry(
            String type,
            UUID referenceId,
            LocalDate date,
            String description,
            int minutes,
            int runningBalanceMinutes) {}

    @Schema(name = "PeriodStatementResponse")
    public record PeriodStatementResponse(
            UUID periodId, PeriodBalanceResponse balance, List<PeriodStatementEntry> entries) {}

    /**
     * Registro de horas de um período, na forma que o banco de horas consome.
     *
     * <p>Serve a dois usos com exigências idênticas: o <b>extrato explicativo</b>, que mostra ao
     * cliente de onde veio cada minuto consumido, e o <b>payload do snapshot</b>, que congela
     * exatamente esses dados no fechamento (entities.md §6.9). Um DTO por uso significaria duas
     * definições do mesmo conteúdo, e a divergência apareceria como um relatório fechado diferente
     * do extrato que o originou.
     *
     * <p>Declarado aqui, e não em {@code worklog}, porque acompanha {@link
     * com.devtime.contract.PeriodWorkLogSource}: é {@code contract} quem define o que precisa
     * receber, e {@code worklog} quem preenche (AR-09).
     *
     * @param description texto livre; entra no snapshot por exigência de §6.9 e <b>nunca</b> em log
     */
    @Schema(name = "PeriodWorkLogEntry")
    public record PeriodWorkLogEntry(
            UUID id,
            LocalDate workDate,
            String ticketKey,
            String categoryName,
            UUID userId,
            String description,
            int netMinutes,
            int billableMinutes,
            boolean billable) {}

    /** Ajuste aplicado (entities.md §6.8). Não existe rota de edição nem de exclusão (RN-236). */
    @Schema(name = "AdjustmentResponse")
    public record AdjustmentResponse(
            UUID id,
            UUID contractPeriodId,
            int minutes,
            String reason,
            String justification,
            UUID appliedBy,
            Instant appliedAt) {}

    /**
     * Resumo do fechamento (RN-241).
     *
     * @param reconciliationDeltaMinutes diferença entre o desnormalizado e a agregação real do
     *     passo 1; diferente de zero indica falha de incremento e gera alerta (FA-16)
     */
    @Schema(name = "ClosePeriodResponse")
    public record ClosePeriodResponse(
            UUID periodId,
            String status,
            int consumedReconciledMinutes,
            int reconciliationDeltaMinutes,
            int carriedOutMinutes,
            int lockedWorkLogs,
            String snapshotChecksum,
            Instant closedAt) {}

    /** Resumo da reabertura (RN-242, RN-243). */
    @Schema(name = "ReopenPeriodResponse")
    public record ReopenPeriodResponse(
            UUID periodId,
            String status,
            int reopenCount,
            int unlockedWorkLogs,
            Instant reopenedAt) {}

    /**
     * Snapshot congelado do período (entities.md §6.9).
     *
     * @param checksumValid verificação sob demanda; falso é alerta operacional, nunca correção
     *     automática (CX-21)
     */
    @Schema(name = "PeriodSnapshotResponse")
    public record PeriodSnapshotResponse(
            UUID id,
            UUID contractPeriodId,
            Instant snapshotAt,
            int schemaVersion,
            String checksum,
            boolean checksumValid,
            String payload) {}
}
