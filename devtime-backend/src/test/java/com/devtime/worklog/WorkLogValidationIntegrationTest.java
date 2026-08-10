package com.devtime.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.support.FeatureTestSupport;
import com.devtime.support.WorkLogScenario;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogValidateRequest;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogValidateResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Validação prévia, sem persistir (§6.3 de worklogs.md, T-008-24).
 *
 * <p>É o que o formulário chama enquanto a pessoa digita, e a razão de existir é dizer "isto vai
 * falhar" <b>antes</b> de a pessoa perder o que escreveu. Duas propriedades importam e nenhuma
 * tinha teste: o resultado precisa coincidir com o que a criação faria — uma validação otimista
 * demais é pior que nenhuma, porque promete sucesso e entrega erro — e <b>nada</b> pode ser gravado
 * no caminho.
 */
class WorkLogValidationIntegrationTest extends FeatureTestSupport {

    @Autowired private WorkLogValidationService validationService;
    @Autowired private WorkLogService workLogService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("§6.3: registro válido é aprovado e o cálculo acompanha a resposta")
    void validRequestIsApprovedWithCalculation() {
        var setup = asOwnerOfA(scenario::create);

        WorkLogValidateResponse resposta =
                asOwnerOfA(() -> validationService.validate(pedido(setup, 9, 0, 11, 0)));

        assertThat(resposta.valid()).isTrue();
        assertThat(resposta.errors()).isEmpty();
        assertThat(resposta.calculated().netMinutes())
                .as("RN-110: o cálculo devolvido é o mesmo que a criação aplicaria")
                .isEqualTo(120);
    }

    @Test
    @DisplayName("RN-102: a validação aponta a sobreposição e devolve o registro conflitante")
    void overlapIsReportedWithTheConflictingEntry() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(
                () ->
                        workLogService.create(
                                new WorkLogCreateRequest(
                                        setup.ticket().id(),
                                        WorkLogScenario.at(9, 0),
                                        WorkLogScenario.at(11, 0),
                                        0,
                                        "Registro já existente",
                                        setup.category().id(),
                                        true,
                                        List.of(),
                                        null)));

        WorkLogValidateResponse resposta =
                asOwnerOfA(() -> validationService.validate(pedido(setup, 10, 0, 12, 0)));

        assertThat(resposta.valid()).isFalse();
        assertThat(resposta.conflicts())
                .as("apontar o conflito é o que permite a pessoa corrigir sem adivinhar")
                .isNotEmpty();
    }

    @Test
    @DisplayName("§6.3 / CP-13: validar não grava nada, nem quando o pedido é válido")
    void validationPersistsNothing() {
        var setup = asOwnerOfA(scenario::create);

        asOwnerOfA(() -> validationService.validate(pedido(setup, 9, 0, 11, 0)));
        asOwnerOfA(() -> validationService.validate(pedido(setup, 13, 0, 15, 0)));

        assertThat(
                        asOwnerOfA(
                                () ->
                                        workLogService.search(
                                                com.devtime.worklog.dto.WorkLogFilter.empty(),
                                                org.springframework.data.domain.PageRequest.of(
                                                        0, 10))))
                .extracting(pagina -> pagina.content().size())
                .as("duas validações e nenhum registro — é o contrato de uma prévia")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("RN-115: intervalo que não produz minutos líquidos é recusado na prévia")
    void nonPositiveNetMinutesIsRejected() {
        var setup = asOwnerOfA(scenario::create);

        WorkLogValidateResponse resposta =
                asOwnerOfA(
                        () ->
                                validationService.validate(
                                        new WorkLogValidateRequest(
                                                setup.ticket().id(),
                                                WorkLogScenario.at(9, 0),
                                                WorkLogScenario.at(9, 30),
                                                40,
                                                "Pausa maior que a sessão",
                                                setup.category().id(),
                                                true,
                                                null,
                                                null)));

        assertThat(resposta.valid()).isFalse();
        assertThat(resposta.errors()).isNotEmpty();
    }

    private WorkLogValidateRequest pedido(
            WorkLogScenario.Scenario setup,
            int horaInicio,
            int minutoInicio,
            int horaFim,
            int minutoFim) {
        return new WorkLogValidateRequest(
                setup.ticket().id(),
                WorkLogScenario.at(horaInicio, minutoInicio),
                WorkLogScenario.at(horaFim, minutoFim),
                0,
                "Registro em digitação",
                setup.category().id(),
                true,
                null,
                null);
    }
}
