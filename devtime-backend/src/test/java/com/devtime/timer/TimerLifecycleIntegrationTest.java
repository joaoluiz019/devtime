package com.devtime.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.WorkLogScenario;
import com.devtime.timer.dto.TimerRequests.TimerRecoverRequest;
import com.devtime.timer.dto.TimerRequests.TimerStartRequest;
import com.devtime.timer.dto.TimerRequests.TimerStopRequest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Ciclo de vida do cronômetro além do caminho feliz (RN-160 a RN-166, spec 009).
 *
 * <p>O que faltava cobertura é justamente o que RP-02 classifica como risco alto: <b>perda de tempo
 * trabalhado</b>. Abandono, recuperação, descarte e encerramento forçado são os quatro caminhos em
 * que minutos já contados podem desaparecer, e nenhum deles tinha teste de integração.
 *
 * <p>O perfil {@code scheduler} entra porque dois desses caminhos só existem por meio de jobs.
 */
@ActiveProfiles({"test", "scheduler"})
class TimerLifecycleIntegrationTest extends FeatureTestSupport {

    @Autowired private TimerService timerService;
    @Autowired private TimerMonitorJob monitorJob;
    @Autowired private AbandonedTimerCleanupJob cleanupJob;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("RN-164: passadas as horas de limite o monitor marca o cronômetro como abandonado")
    void monitorMarksLongRunningTimerAsAbandoned() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(inicio(setup), false));

        clock.advance(Duration.ofHours(17));
        assertThatCode(() -> monitorJob.monitor()).doesNotThrowAnyException();

        assertThat(asOwnerOfA(() -> timerService.abandoned()))
                .as(
                        "RN-164 marca, não encerra: encerrar com horário inventado registraria horas"
                                + " que ninguém trabalhou")
                .isNotEmpty();
    }

    @Test
    @DisplayName("RN-160/RN-165: recuperação recusada não faz o cronômetro abandonado desaparecer")
    void failedRecoveryKeepsTheAbandonedTimerRecoverable() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(inicio(setup), false));
        clock.advance(Duration.ofHours(17));
        monitorJob.monitor();

        var abandonado = asOwnerOfA(() -> timerService.abandoned()).get(0);

        // O contrato do cenário tem período em janeiro e o relógio dos testes está em julho: a
        // recuperação cai em RN-107, que é exatamente a falha que interessa verificar aqui. O
        // caminho de sucesso já é coberto por TimerServiceIntegrationTest, com data dentro do
        // período.
        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                timerService.recover(
                                                        abandonado.id(),
                                                        new TimerRecoverRequest(
                                                                NOW.plus(Duration.ofHours(2)),
                                                                "Sessão recuperada pelo dono"))))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(asOwnerOfA(() -> timerService.abandoned()))
                .as("RN-160: uma recuperação recusada não pode consumir o tempo trabalhado")
                .isNotEmpty();
    }

    @Test
    @DisplayName("RN-162: o descarte exige confirmação — sem ela, nada é perdido")
    void discardRequiresConfirmation() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(inicio(setup), false));
        clock.advance(Duration.ofMinutes(30));

        assertThatThrownBy(() -> asOwnerOfA(() -> discard(false)))
                .as("é a única operação do produto que destrói trabalho registrado")
                .isInstanceOf(BusinessRuleException.class);

        asOwnerOfA(() -> discard(true));
        assertThat(asOwnerOfA(() -> timerService.abandoned())).isEmpty();
    }

    @Test
    @DisplayName("RN-166: iniciar com cronômetro ativo exige a troca explícita")
    void startingWithActiveTimerRequiresExplicitSwitch() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(inicio(setup), false));

        assertThatThrownBy(() -> asOwnerOfA(() -> timerService.start(inicio(setup), false)))
                .as("trocar de tarefa por acidente encerraria a sessão anterior sem aviso")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("RN-158: encerrar sem descrição falha e o cronômetro permanece ativo (RN-160)")
    void stopWithoutDescriptionKeepsTimerAlive() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(inicio(setup), false));
        clock.advance(Duration.ofMinutes(20));

        assertThatThrownBy(() -> asOwnerOfA(() -> timerService.stop(new TimerStopRequest(null))))
                .isInstanceOf(BusinessRuleException.class);

        // RN-160 aplicada por construção: o estado só muda depois de o work log existir. Como a
        // tentativa foi recusada antes disso, o cronômetro tem de continuar contando.
        assertThat(asOwnerOfA(() -> timerService.pause()).status())
                .as("o tempo sobreviveu à tentativa recusada — é o que RN-160 promete")
                .isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("BR-185: a limpeza de abandonados converge ao ser reexecutada")
    void cleanupIsConvergent() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> timerService.start(inicio(setup), false));
        clock.advance(Duration.ofDays(9));

        assertThatCode(
                        () -> {
                            cleanupJob.discardExpiredAbandoned();
                            cleanupJob.discardExpiredAbandoned();
                        })
                .doesNotThrowAnyException();
    }

    private Object discard(boolean confirmado) {
        timerService.discard(confirmado);
        return null;
    }

    private TimerStartRequest inicio(WorkLogScenario.Scenario setup) {
        return new TimerStartRequest(
                setup.ticket().id(), setup.category().id(), "Sessão em andamento", true);
    }
}
