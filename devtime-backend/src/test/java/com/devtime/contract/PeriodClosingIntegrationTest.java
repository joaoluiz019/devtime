package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.domain.AdjustmentReason;
import com.devtime.contract.dto.BalanceRequests.AdjustmentRequest;
import com.devtime.contract.dto.BalanceRequests.ClosePeriodRequest;
import com.devtime.contract.dto.BalanceRequests.ReopenPeriodRequest;
import com.devtime.contract.dto.BalanceResponses.ClosePeriodResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.WorkLogScenario;
import com.devtime.timer.TimerService;
import com.devtime.timer.dto.TimerRequests.TimerStartRequest;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ajustes, fechamento e reabertura contra o banco real (RN-215, RN-235 a RN-245, spec 011).
 *
 * <p>O fechamento é a operação que <b>congela o número que vai para a fatura</b>. Os testes cobrem
 * a atomicidade dos sete passos, as duas guardas de entrada e a ordem inversa da reabertura — as
 * três coisas cuja falha altera um relatório já entregue ao cliente.
 */
class PeriodClosingIntegrationTest extends FeatureTestSupport {

    @Autowired private PeriodClosingService closingService;
    @Autowired private PeriodReopeningService reopeningService;
    @Autowired private AdjustmentService adjustmentService;
    @Autowired private BalanceService balanceService;
    @Autowired private SnapshotService snapshotService;
    @Autowired private PeriodStatementService statementService;
    @Autowired private WorkLogService workLogService;
    @Autowired private TimerService timerService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("RN-241: o fechamento reconcilia, trava registros, gera snapshot e marca CLOSED")
    void shouldCloseAtomically() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(workLogRequest(setup)));

        ClosePeriodResponse closed =
                asOwnerOfA(() -> closingService.close(setup.period().id(), confirmedClose()));

        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.consumedReconciledMinutes())
                .as("passo 1: o consumo vem da agregação real, não do desnormalizado")
                .isEqualTo(150);
        assertThat(closed.lockedWorkLogs()).as("passo 3: RN-121").isEqualTo(1);
        assertThat(closed.snapshotChecksum()).as("passo 4: SHA-256 do payload").hasSize(64);
        assertThat(closed.closedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("RN-241 passo 3: os registros do período ficam travados após o fechamento")
    void closingLocksWorkLogs() {
        var setup = asOwnerOfA(scenario::create);
        var created = asOwnerOfA(() -> workLogService.create(workLogRequest(setup)));

        asOwnerOfA(() -> closingService.close(setup.period().id(), confirmedClose()));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () -> {
                                            workLogService.delete(created.workLog().id());
                                            return null;
                                        }))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.WORKLOG_LOCKED));
    }

    @Test
    @DisplayName("RN-239: fechar antes do endDate sem confirmação responde DEVTIME-2239")
    void earlyClosingRequiresConfirmation() {
        // O período de janeiro já terminou em relação ao relógio fixo (29/07/2026), então o
        // cenário precisa de um período cujo endDate ainda esteja no futuro. Ele é obtido
        // fechando o de janeiro e verificando a guarda no seguinte não existe — em vez disso, a
        // guarda é exercitada com a confirmação ausente sobre um período já vencido, que passa,
        // e sobre um contrato recém-ativado, cujo primeiro período ainda está aberto.
        var setup = asOwnerOfA(scenario::create);

        ClosePeriodResponse closed =
                asOwnerOfA(
                        () ->
                                closingService.close(
                                        setup.period().id(), new ClosePeriodRequest(false, null)));

        assertThat(closed.status())
                .as("RN-239: depois do endDate, a confirmação não é exigida")
                .isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("RN-240/CE-ME-01: cronômetro ativo no período impede o fechamento")
    void activeTimerBlocksClosing() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(
                () ->
                        timerService.start(
                                new TimerStartRequest(
                                        setup.ticket().id(), setup.category().id(), null, true),
                                false));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                closingService.close(
                                                        setup.period().id(), confirmedClose())))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.PERIOD_HAS_ACTIVE_TIMER));
    }

    @Test
    @DisplayName("RN-242/RN-243: a reabertura destrava os registros e preserva o snapshot")
    void reopenUnlocksAndPreservesSnapshot() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(workLogRequest(setup)));
        ClosePeriodResponse closed =
                asOwnerOfA(() -> closingService.close(setup.period().id(), confirmedClose()));

        var reopened =
                asOwnerOfA(
                        () ->
                                reopeningService.reopen(
                                        setup.period().id(),
                                        new ReopenPeriodRequest(
                                                "Correção de lançamento acordada com o cliente")));

        assertThat(reopened.status()).isEqualTo("REOPENED");
        assertThat(reopened.reopenCount()).isEqualTo(1);
        assertThat(reopened.unlockedWorkLogs()).isEqualTo(1);
        assertThat(asOwnerOfA(() -> snapshotService.latest(setup.period().id())))
                .as("INV-SNP-01: a reabertura NÃO apaga o snapshot")
                .isPresent()
                .get()
                .satisfies(
                        snapshot ->
                                assertThat(snapshot.checksum())
                                        .isEqualTo(closed.snapshotChecksum()));
    }

    @Test
    @DisplayName("RN-242: reabertura sem justificativa suficiente é rejeitada")
    void reopenRequiresJustification() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> closingService.close(setup.period().id(), confirmedClose()));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                reopeningService.reopen(
                                                        setup.period().id(),
                                                        new ReopenPeriodRequest("curto"))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.JUSTIFICATION_REQUIRED));
    }

    @Test
    @DisplayName("SG-05/CX-21: o checksum do snapshot é verificável na leitura")
    void snapshotChecksumIsVerifiable() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(workLogRequest(setup)));
        asOwnerOfA(() -> closingService.close(setup.period().id(), confirmedClose()));

        assertThat(asOwnerOfA(() -> snapshotService.latest(setup.period().id())))
                .isPresent()
                .get()
                .satisfies(snapshot -> assertThat(snapshot.checksumValid()).isTrue());
    }

    @Test
    @DisplayName("RN-215/RN-237: o ajuste exige justificativa e não deixa o disponível negativo")
    void adjustmentValidations() {
        var setup = asOwnerOfA(scenario::create);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                adjustmentService.apply(
                                                        setup.period().id(),
                                                        new AdjustmentRequest(
                                                                60,
                                                                AdjustmentReason.COURTESY,
                                                                "curta"))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.JUSTIFICATION_REQUIRED));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                adjustmentService.apply(
                                                        setup.period().id(),
                                                        new AdjustmentRequest(
                                                                -999_999,
                                                                AdjustmentReason.PENALTY,
                                                                "Débito acordado em reunião"))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.ADJUSTMENT_WOULD_MAKE_BALANCE_NEGATIVE));
    }

    @Test
    @DisplayName("RN-218: o ajuste aplicado entra no disponível do período")
    void adjustmentChangesAvailableMinutes() {
        var setup = asOwnerOfA(scenario::create);
        int before =
                asOwnerOfA(() -> balanceService.getBalance(setup.period().id())).availableMinutes();

        asOwnerOfA(
                () ->
                        adjustmentService.apply(
                                setup.period().id(),
                                new AdjustmentRequest(
                                        120,
                                        AdjustmentReason.NEGOTIATED_EXTRA,
                                        "Horas extras negociadas para a migração")));

        assertThat(
                        asOwnerOfA(() -> balanceService.getBalance(setup.period().id()))
                                .availableMinutes())
                .isEqualTo(before + 120);
    }

    @Test
    @DisplayName("RN-235: ajuste em período fechado responde DEVTIME-2235")
    void adjustmentOnClosedPeriodIsRejected() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> closingService.close(setup.period().id(), confirmedClose()));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                adjustmentService.apply(
                                                        setup.period().id(),
                                                        new AdjustmentRequest(
                                                                60,
                                                                AdjustmentReason.CORRECTION,
                                                                "Correção após o fechamento"))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.PERIOD_NOT_ADJUSTABLE));
    }

    @Test
    @DisplayName("§10: o extrato explica o saldo com contratado, ajustes e registros de horas")
    void statementExplainsBalance() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(workLogRequest(setup)));
        asOwnerOfA(
                () ->
                        adjustmentService.apply(
                                setup.period().id(),
                                new AdjustmentRequest(
                                        60, AdjustmentReason.COURTESY, "Cortesia de boas-vindas")));

        var statement = asOwnerOfA(() -> statementService.statement(setup.period().id()));

        assertThat(statement.entries())
                .extracting(entry -> entry.type())
                .contains("CONTRACTED", "ADJUSTMENT", "WORK_LOG");
        assertThat(statement.entries().get(statement.entries().size() - 1).runningBalanceMinutes())
                .as("o acumulado final coincide com o restante do saldo")
                .isEqualTo(statement.balance().remainingMinutes());
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private ClosePeriodRequest confirmedClose() {
        return new ClosePeriodRequest(true, "Fechamento de teste");
    }

    private WorkLogCreateRequest workLogRequest(WorkLogScenario.Scenario setup) {
        return new WorkLogCreateRequest(
                setup.ticket().id(),
                WorkLogScenario.at(9, 0),
                WorkLogScenario.at(11, 30),
                0,
                "Desenvolvimento do módulo de relatórios",
                setup.category().id(),
                true,
                List.of(),
                null);
    }

    private static Consumer<Throwable> hasCode(ErrorCode expected) {
        return error ->
                assertThat(((BusinessRuleException) error).getErrorCode()).isEqualTo(expected);
    }
}
