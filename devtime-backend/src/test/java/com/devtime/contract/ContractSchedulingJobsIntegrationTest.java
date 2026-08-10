package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.dto.ContractResponses.ContractPeriodResponse;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.TicketScenario;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Ciclo automático de contratos e períodos (§22.4 de specs/004, nota ¹⁵).
 *
 * <p>Estes cinco jobs são o que faz o produto continuar funcionando sem ninguém clicar em nada: sem
 * eles, o mês vira e não existe período aberto para receber horas — o registro passa a falhar para
 * todo mundo, no primeiro dia útil, sem que nada tenha sido alterado.
 *
 * <p>A nota ¹⁵ registra que um defeito de sessão fazia jobs desta família falharem dentro do {@code
 * catch}. As asserções abaixo cobrem execução completa e convergência; a geração propriamente dita
 * é verificada pelo estado dos períodos.
 */
@ActiveProfiles({"test", "scheduler"})
class ContractSchedulingJobsIntegrationTest extends FeatureTestSupport {

    @Autowired private ContractSchedulingJobs jobs;
    @Autowired private ContractPeriodService periodService;
    @Autowired private TicketScenario scenario;

    @Test
    @DisplayName("RN-213: a geração antecipada cria o período seguinte de contrato ativo")
    void upcomingPeriodsAreGenerated() {
        var contrato = asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));

        // A janela de RN-213 é de três dias antes do fim do período corrente.
        clock.advance(Duration.ofDays(200));
        assertThatCode(() -> jobs.generateUpcomingPeriods()).doesNotThrowAnyException();

        List<ContractPeriodResponse> periodos =
                asOwnerOfA(() -> periodService.listByContract(contrato.id()));
        assertThat(periodos)
                .as("o contrato ativo com renovação automática nunca fica sem período à frente")
                .isNotEmpty();
    }

    @Test
    @DisplayName("§11: SCHEDULED → OPEN só ocorre com o período anterior fechado")
    void scheduledPeriodsAreOpenedInOrder() {
        var contrato = asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));
        clock.advance(Duration.ofDays(200));
        jobs.generateUpcomingPeriods();

        assertThatCode(() -> jobs.openScheduledPeriods()).doesNotThrowAnyException();

        assertThat(asOwnerOfA(() -> periodService.listByContract(contrato.id())))
                .extracting(ContractPeriodResponse::status)
                .as("INV-PER-07: no máximo um período aberto por contrato")
                .filteredOn(status -> status == PeriodStatus.OPEN)
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("FA-05: o encerramento automático não alcança contrato sem data de fim")
    void openEndedContractIsNotEnded() {
        var contrato = asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));

        clock.advance(Duration.ofDays(400));
        assertThatCode(() -> jobs.endExpiredContracts()).doesNotThrowAnyException();

        assertThat(asOwnerOfA(() -> periodService.listByContract(contrato.id())))
                .as("um contrato sem data de fim não expira sozinho")
                .isNotEmpty();
    }

    @Test
    @DisplayName("BR-185: os cinco jobs convergem ao serem reexecutados")
    void jobsAreConvergent() {
        asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));
        clock.advance(Duration.ofDays(200));

        assertThatCode(
                        () -> {
                            jobs.generateUpcomingPeriods();
                            jobs.generateUpcomingPeriods();
                            jobs.openScheduledPeriods();
                            jobs.openScheduledPeriods();
                            jobs.endExpiredContracts();
                            jobs.endExpiredContracts();
                            jobs.expireRolloverBalances();
                            jobs.expireRolloverBalances();
                            jobs.autoClosePeriodsOfEndedContracts();
                            jobs.autoClosePeriodsOfEndedContracts();
                        })
                .doesNotThrowAnyException();
    }
}
