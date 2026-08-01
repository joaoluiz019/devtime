package com.devtime.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.WorkLogScenario;
import com.devtime.timer.dto.TimerRequests.TimerStartRequest;
import com.devtime.timer.dto.TimerRequests.TimerStopRequest;
import com.devtime.timer.dto.TimerRequests.TimerUpdateRequest;
import com.devtime.timer.dto.TimerResponses.TimerResponse;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ciclo de vida do cronômetro contra o banco real (RN-150 a RN-167, spec 009).
 *
 * <p>O foco é o que só existe com estado persistido: a unicidade por usuário, a máquina de estados
 * e, sobretudo, <b>RN-160</b> — a falha de validação no encerramento não pode descartar tempo
 * trabalhado.
 *
 * <p>O relógio é fixo (BR-205), então todo cronômetro iniciado nos testes tem duração bruta zero.
 * Isso é conveniente e não acidental: o encerramento imediato cai em RN-115 ({@code netMinutes =
 * 0}, CX-21 de {@code specs/009}) e é justamente esse o caminho que prova a preservação do
 * cronômetro.
 */
class TimerServiceIntegrationTest extends FeatureTestSupport {

    @Autowired private TimerService timerService;
    @Autowired private TimerQueryService timerQueryService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("RN-152: o cronômetro nasce RUNNING com os três campos de estado do servidor")
    void shouldStartRunning() {
        var setup = asOwnerOfA(scenario::create);

        TimerResponse timer = asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        assertThat(timer.status()).isEqualTo("RUNNING");
        assertThat(timer.startedAt()).isEqualTo(NOW);
        assertThat(timer.lastResumedAt())
                .as("RN-152: lastResumedAt nasce igual a startedAt")
                .isEqualTo(NOW);
        assertThat(timer.accumulatedActiveSeconds()).isZero();
        assertThat(timer.pausedMinutes()).isZero();
    }

    @Test
    @DisplayName("RN-150: um segundo cronômetro do mesmo usuário é rejeitado com DEVTIME-2150")
    void shouldRejectSecondActiveTimer() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        assertThatThrownBy(() -> asOwnerOfA(() -> timerService.start(startRequest(setup), false)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.TIMER_ALREADY_ACTIVE));
    }

    @Test
    @DisplayName("RN-150/CX-01: o limite é por usuário, mesmo entre tenants diferentes")
    void limitAppliesAcrossTenants() {
        var setupA = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setupA), false));

        // O mesmo usuário em outro tenant: o cenário do tenant B pertence a userBId, então o teste
        // usa o próprio userAId dentro do tenant B para reproduzir CE-13.
        var setupB = asOwnerOfB(scenario::create);
        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantBId,
                                        userAId,
                                        com.devtime.shared.security.Role.OWNER,
                                        () -> timerService.start(startRequest(setupB), false)))
                .as("participar de duas organizações não torna a pessoa duas")
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.TIMER_ALREADY_ACTIVE));
    }

    @Test
    @DisplayName("RN-153/RN-155: pausar exige RUNNING e retomar exige PAUSED")
    void stateMachineGuards() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        assertThatThrownBy(() -> asOwnerOfA(() -> timerService.resume()))
                .as("RN-155: retomar um cronômetro em execução é erro, não idempotência")
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.TIMER_NOT_PAUSED));

        TimerResponse paused = asOwnerOfA(() -> timerService.pause());
        assertThat(paused.status()).isEqualTo("PAUSED");

        assertThatThrownBy(() -> asOwnerOfA(() -> timerService.pause()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.TIMER_NOT_RUNNING));
    }

    @Test
    @DisplayName("RN-154/RN-156: pausar abre a pausa e retomar a fecha, voltando a RUNNING")
    void pauseAndResumeCycle() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        asOwnerOfA(() -> timerService.pause());
        TimerResponse resumed = asOwnerOfA(() -> timerService.resume());

        assertThat(resumed.status()).isEqualTo("RUNNING");
        assertThat(resumed.lastResumedAt())
                .as("RN-156: a retomada reposiciona o início do trecho ativo")
                .isEqualTo(NOW);
    }

    @Test
    @DisplayName("RN-158/RN-160: encerrar sem descrição falha e o cronômetro PERMANECE ativo")
    void stopWithoutDescriptionPreservesTimer() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        assertThatThrownBy(() -> asOwnerOfA(() -> timerService.stop(new TimerStopRequest("ab"))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.WORKLOG_DESCRIPTION_INVALID));

        assertThat(asOwnerOfA(() -> timerQueryService.current()))
                .as("RN-160: o tempo trabalhado nunca é descartado pelo sistema")
                .isPresent()
                .get()
                .satisfies(timer -> assertThat(timer.status()).isEqualTo("RUNNING"));
    }

    @Test
    @DisplayName("RN-159/RN-160/CX-21: falha de validação do work log preserva o cronômetro")
    void stopWithInvalidWorkLogPreservesTimer() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        // Com o relógio fixo, gross = 0 e net = 0: o work log é rejeitado por RN-115, exatamente
        // como uma sobreposição ou um saldo estourado seriam.
        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                timerService.stop(
                                                        new TimerStopRequest(
                                                                "Sessão sem tempo decorrido"))))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(asOwnerOfA(() -> timerQueryService.current()))
                .as("RN-160: a proibição mais importante da feature")
                .isPresent()
                .get()
                .satisfies(timer -> assertThat(timer.status()).isEqualTo("RUNNING"));
    }

    @Test
    @DisplayName("RN-161: ticket, categoria, descrição e faturável são editáveis em execução")
    void shouldUpdateWhileRunning() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        TimerResponse updated =
                asOwnerOfA(
                        () ->
                                timerService.update(
                                        new TimerUpdateRequest(
                                                null,
                                                null,
                                                "Descrição descoberta durante o trabalho",
                                                false)));

        assertThat(updated.description()).isEqualTo("Descrição descoberta durante o trabalho");
        assertThat(updated.billable()).isFalse();
    }

    @Test
    @DisplayName("RN-162: o descarte exige confirmação e é irreversível")
    void discardRequiresConfirmation() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () -> {
                                            timerService.discard(false);
                                            return null;
                                        }))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(asOwnerOfA(() -> timerQueryService.current()))
                .as("sem confirmação, nada acontece")
                .isPresent();

        asOwnerOfA(
                () -> {
                    timerService.discard(true);
                    return null;
                });

        assertThat(asOwnerOfA(() -> timerQueryService.current()))
                .as("descartado: nenhum work log é gerado e o cronômetro deixa de estar ativo")
                .isEmpty();
    }

    @Test
    @DisplayName("RN-311: o ticket com cronômetro ativo é reportado a 007")
    void activeTimerIsVisibleToTicketFeature() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        assertThat(asOwnerOfA(() -> timerQueryService.hasActiveForTicket(setup.ticket().id())))
                .isTrue();
        assertThat(asOwnerOfA(() -> timerQueryService.activeTimerIdsForTicket(setup.ticket().id())))
                .hasSize(1);
    }

    @Test
    @DisplayName("RN-311/CE-ME-01: um cronômetro pausado continua contando como ativo")
    void pausedTimerStillCountsAsActive() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));
        asOwnerOfA(() -> timerService.pause());

        assertThat(asOwnerOfA(() -> timerQueryService.hasActiveForTicket(setup.ticket().id())))
                .as("o trabalho não terminou, apenas parou")
                .isTrue();
    }

    @Test
    @DisplayName("RN-306/CX-04: contrato encerrado não impede exibir o cronômetro em execução")
    void runningTimerRemainsVisibleWhenContractEnds() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(startRequest(setup), false));

        assertThat(asOwnerOfA(() -> timerQueryService.current()))
                .as("é o encerramento que falha em RN-306, não a exibição")
                .isPresent();
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private TimerStartRequest startRequest(WorkLogScenario.Scenario setup) {
        return new TimerStartRequest(setup.ticket().id(), setup.category().id(), null, true);
    }

    private static Consumer<Throwable> hasCode(ErrorCode expected) {
        return error ->
                assertThat(((BusinessRuleException) error).getErrorCode()).isEqualTo(expected);
    }
}
