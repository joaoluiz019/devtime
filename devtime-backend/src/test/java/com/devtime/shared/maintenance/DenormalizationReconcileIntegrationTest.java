package com.devtime.shared.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.contract.ContractPeriodRepository;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.WorkLogScenario;
import com.devtime.ticket.TicketRepository;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Reconciliação noturna dos desnormalizados (specs 003, 006, 007 e 011, §22.4).
 *
 * <p>O teste corrompe deliberadamente o valor persistido e verifica que o reconciliador o traz de
 * volta ao real. É a única forma de exercitá-lo: no caminho normal o incremento transacional já
 * mantém o número certo, e um teste que só rodasse o job sobre dados íntegros passaria mesmo se o
 * reconciliador não fizesse nada.
 */
class DenormalizationReconcileIntegrationTest extends FeatureTestSupport {

    @Autowired private List<DenormalizationReconciler> reconcilers;
    @Autowired private WorkLogService workLogService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ContractPeriodRepository periodRepository;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("§22.4: os quatro reconciliadores estão registrados no job compartilhado")
    void everyReconcilerIsRegistered() {
        assertThat(reconcilers)
                .extracting(DenormalizationReconciler::target)
                .contains(
                        "client.activeContractsCount",
                        "tag.usageCount",
                        "ticket.spentMinutes",
                        "contractPeriod.consumedMinutes");
    }

    @Test
    @DisplayName("RN-308: spentMinutes divergente volta ao valor da agregação real")
    void ticketTotalsAreReconciled() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup)));
        UUID ticketId = setup.ticket().id();

        corruptTicketTotals(ticketId);
        int corrected = reconcilerFor("ticket.spentMinutes").reconcile();

        assertThat(corrected).isPositive();
        assertThat(asOwnerOfA(() -> ticketRepository.findById(ticketId).orElseThrow()))
                .satisfies(
                        ticket -> {
                            assertThat(ticket.getSpentMinutes()).isEqualTo(150);
                            assertThat(ticket.getBillableMinutes()).isEqualTo(150);
                        });
    }

    @Test
    @DisplayName("spec 011 §22.4: consumedMinutes de período aberto volta à agregação real")
    void openPeriodConsumptionIsReconciled() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup)));
        UUID periodId = setup.period().id();

        asOwnerOfA(
                () -> {
                    var period = periodRepository.findById(periodId).orElseThrow();
                    period.setConsumedMinutes(9999);
                    return periodRepository.save(period);
                });

        int corrected = reconcilerFor("contractPeriod.consumedMinutes").reconcile();

        assertThat(corrected).isPositive();
        assertThat(
                        asOwnerOfA(
                                () ->
                                        periodRepository
                                                .findById(periodId)
                                                .orElseThrow()
                                                .getConsumedMinutes()))
                .isEqualTo(150);
    }

    @Test
    @DisplayName("BR-185: a segunda execução não encontra divergência e não corrige nada")
    void reconciliationIsConvergent() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup)));
        corruptTicketTotals(setup.ticket().id());

        reconcilerFor("ticket.spentMinutes").reconcile();

        assertThat(reconcilerFor("ticket.spentMinutes").reconcile()).isZero();
    }

    private void corruptTicketTotals(UUID ticketId) {
        asOwnerOfA(
                () -> {
                    var ticket = ticketRepository.findById(ticketId).orElseThrow();
                    ticket.setSpentMinutes(0);
                    ticket.setBillableMinutes(0);
                    return ticketRepository.save(ticket);
                });
    }

    private DenormalizationReconciler reconcilerFor(String target) {
        return reconcilers.stream()
                .filter(reconciler -> reconciler.target().equals(target))
                .findFirst()
                .orElseThrow();
    }

    private WorkLogCreateRequest request(WorkLogScenario.Scenario setup) {
        return new WorkLogCreateRequest(
                setup.ticket().id(),
                WorkLogScenario.at(9, 0),
                WorkLogScenario.at(11, 30),
                0,
                "Trabalho a reconciliar",
                setup.category().id(),
                true,
                List.of(),
                null);
    }
}
