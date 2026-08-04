package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.dto.ContractRequests.ContractTransitionRequest;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.contract.dto.ContractResponses.MaintenanceTarget;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.TicketScenario;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T-004-29 e T-004-30: geração, abertura e encerramento automáticos.
 *
 * <p>O teste exercita {@link ContractMaintenanceService}, onde vive a regra, e não a classe de job
 * — aquela é orquestração: varre, define o contexto e delega. CA-11 (idempotência) é verificada
 * pela segunda chamada de cada operação, que é exatamente o que uma reexecução do job faz.
 */
class ContractMaintenanceIntegrationTest extends FeatureTestSupport {

    @Autowired private ContractMaintenanceService maintenanceService;
    @Autowired private ContractService contractService;
    @Autowired private ContractPeriodRepository periodRepository;
    @Autowired private TicketScenario scenario;

    /** Relógio fixo dos testes: {@code 2026-07-29}. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 29);

    private ContractResponse activeContract() {
        return asOwnerOfA(
                () -> {
                    UUID clientId = scenario.activeClient();
                    return scenario.activeContract(clientId);
                });
    }

    private UUID openPeriodOf(UUID contractId) {
        return asOwnerOfA(
                () -> periodRepository.findOpenByContractId(contractId).orElseThrow().getId());
    }

    @Test
    @DisplayName("RN-213: a renovação cria o período seguinte como SCHEDULED e contíguo")
    void renewalCreatesNextPeriodAsScheduled() {
        ContractResponse contract = activeContract();
        UUID openPeriodId = openPeriodOf(contract.id());

        boolean created = asOwnerOfA(() -> maintenanceService.renewPeriod(openPeriodId));

        assertThat(created).isTrue();
        var periods =
                asOwnerOfA(() -> periodRepository.findByContractIdOrderBySequence(contract.id()));
        assertThat(periods).hasSize(2);
        assertThat(periods.get(1).getStatus()).isEqualTo(PeriodStatus.SCHEDULED);
        assertThat(periods.get(1).getSequence()).isEqualTo(periods.get(0).getSequence() + 1);
        // INV-PER-03: sem lacuna entre o fim de um e o início do seguinte.
        assertThat(periods.get(1).getStartDate())
                .isEqualTo(periods.get(0).getEndDate().plusDays(1));
    }

    @Test
    @DisplayName("CA-11: reexecutar a renovação não duplica o período")
    void renewalIsIdempotent() {
        ContractResponse contract = activeContract();
        UUID openPeriodId = openPeriodOf(contract.id());

        asOwnerOfA(() -> maintenanceService.renewPeriod(openPeriodId));
        boolean secondRun = asOwnerOfA(() -> maintenanceService.renewPeriod(openPeriodId));

        assertThat(secondRun).isFalse();
        assertThat(
                        asOwnerOfA(
                                () ->
                                        periodRepository.findByContractIdOrderBySequence(
                                                contract.id())))
                .hasSize(2);
    }

    @Test
    @DisplayName("§11: a abertura é adiada enquanto o período anterior não está CLOSED")
    void openingWaitsForPreviousPeriodToClose() {
        ContractResponse contract = activeContract();
        UUID openPeriodId = openPeriodOf(contract.id());
        asOwnerOfA(() -> maintenanceService.renewPeriod(openPeriodId));
        UUID scheduledId = scheduledPeriodOf(contract.id());

        // O período anterior continua OPEN: abrir agora violaria uq_periods_single_open.
        assertThat(asOwnerOfA(() -> maintenanceService.openScheduledPeriod(scheduledId))).isFalse();
        assertThat(asOwnerOfA(() -> statusOf(scheduledId))).isEqualTo(PeriodStatus.SCHEDULED);
    }

    @Test
    @DisplayName("§22.4: com o anterior CLOSED, abre; a segunda execução não faz nada (CA-11)")
    void openingIsIdempotent() {
        ContractResponse contract = activeContract();
        UUID openPeriodId = openPeriodOf(contract.id());
        asOwnerOfA(() -> maintenanceService.renewPeriod(openPeriodId));
        UUID scheduledId = scheduledPeriodOf(contract.id());
        closeDirectly(openPeriodId);

        assertThat(asOwnerOfA(() -> maintenanceService.openScheduledPeriod(scheduledId))).isTrue();
        assertThat(asOwnerOfA(() -> maintenanceService.openScheduledPeriod(scheduledId))).isFalse();
        assertThat(asOwnerOfA(() -> statusOf(scheduledId))).isEqualTo(PeriodStatus.OPEN);
    }

    private UUID scheduledPeriodOf(UUID contractId) {
        return asOwnerOfA(
                () -> periodRepository.findByContractIdOrderBySequence(contractId).get(1).getId());
    }

    private PeriodStatus statusOf(UUID periodId) {
        return periodRepository.findById(periodId).orElseThrow().getStatus();
    }

    /**
     * Fecha o período pelo repositório, sem passar pelo fechamento de {@code 011}.
     *
     * <p>O que este teste precisa é do <b>estado</b> {@code CLOSED} como pré-condição da guarda de
     * §11; o fechamento completo — reconciliação, carry-over, snapshot — é regra de {@code 011} e
     * tem suíte própria. Envolvê-lo aqui acoplaria este teste a sete passos que ele não verifica.
     */
    private void closeDirectly(UUID periodId) {
        asOwnerOfA(
                () -> {
                    var period = periodRepository.findById(periodId).orElseThrow();
                    period.setStatus(PeriodStatus.CLOSED);
                    // ck_periods_closed_requires_actor: um período fechado registra quando e por
                    // quem. A constraint recusa a linha sem os dois.
                    period.setClosedAt(NOW);
                    period.setClosedBy(userAId);
                    return periodRepository.save(period);
                });
    }

    @Test
    @DisplayName("RN-213: a varredura devolve o período vencido e para de devolvê-lo após renovar")
    void renewalScanExcludesPeriodsWithSuccessor() {
        ContractResponse contract = activeContract();
        UUID openPeriodId = openPeriodOf(contract.id());

        List<MaintenanceTarget> before = maintenanceService.findRenewalDue(TODAY, 3, 100);
        assertThat(before).extracting(MaintenanceTarget::entityId).contains(openPeriodId);

        asOwnerOfA(() -> maintenanceService.renewPeriod(openPeriodId));

        List<MaintenanceTarget> after = maintenanceService.findRenewalDue(TODAY, 3, 100);
        assertThat(after).extracting(MaintenanceTarget::entityId).doesNotContain(openPeriodId);
    }

    @Test
    @DisplayName("RN-213: contrato suspenso não entra na varredura de renovação (FA-03)")
    void suspendedContractIsNotRenewed() {
        ContractResponse contract = activeContract();
        UUID openPeriodId = openPeriodOf(contract.id());
        asOwnerOfA(
                () ->
                        contractService.suspend(
                                contract.id(),
                                new ContractTransitionRequest("Pausa acordada", null, null)));

        assertThat(maintenanceService.findRenewalDue(TODAY, 3, 100))
                .extracting(MaintenanceTarget::entityId)
                .doesNotContain(openPeriodId);
    }

    @Test
    @DisplayName("FA-05: contrato com vigência vencida é encerrado e o período é truncado")
    void expiredContractIsEnded() {
        ContractResponse contract = activeContract();
        LocalDate endDate = TODAY.minusDays(1);
        asOwnerOfA(
                () -> contractService.update(contract.id(), updateWithEndDate(contract, endDate)));

        List<MaintenanceTarget> due = maintenanceService.findEndDue(TODAY, 100);
        assertThat(due).extracting(MaintenanceTarget::contractId).contains(contract.id());

        assertThat(asOwnerOfA(() -> maintenanceService.endContract(contract.id()))).isTrue();
        // CA-11: a segunda execução não repete a transição.
        assertThat(asOwnerOfA(() -> maintenanceService.endContract(contract.id()))).isFalse();

        assertThat(asOwnerOfA(() -> contractService.getById(contract.id()).status().name()))
                .isEqualTo("ENDED");
    }

    @Test
    @DisplayName("CE-ME-02: período aberto de contrato encerrado há 3 dias é fechado pelo job")
    void endedContractPeriodIsClosedAutomatically() {
        ContractResponse contract = activeContract();
        UUID openPeriodId = openPeriodOf(contract.id());
        asOwnerOfA(
                () ->
                        contractService.update(
                                contract.id(), updateWithEndDate(contract, TODAY.minusDays(10))));
        asOwnerOfA(() -> maintenanceService.endContract(contract.id()));

        List<MaintenanceTarget> due = maintenanceService.findAutoCloseDue(TODAY, 3, 100);
        assertThat(due).extracting(MaintenanceTarget::entityId).contains(openPeriodId);

        assertThat(asOwnerOfA(() -> maintenanceService.autoClosePeriod(openPeriodId))).isTrue();
        assertThat(asOwnerOfA(() -> statusOf(openPeriodId))).isEqualTo(PeriodStatus.CLOSED);
        // Convergente: o período já fechado sai da varredura e a reexecução não faz nada.
        assertThat(asOwnerOfA(() -> maintenanceService.autoClosePeriod(openPeriodId))).isFalse();
        assertThat(maintenanceService.findAutoCloseDue(TODAY, 3, 100))
                .extracting(MaintenanceTarget::entityId)
                .doesNotContain(openPeriodId);
    }

    @Test
    @DisplayName("CE-ME-02: contrato ainda ACTIVE não tem período fechado automaticamente")
    void activeContractPeriodIsNotAutoClosed() {
        ContractResponse contract = activeContract();
        UUID openPeriodId = openPeriodOf(contract.id());

        assertThat(maintenanceService.findAutoCloseDue(TODAY, 3, 100))
                .extracting(MaintenanceTarget::entityId)
                .doesNotContain(openPeriodId);
    }

    @Test
    @DisplayName(
            "CX-20: contrato com rolloverExpiryPeriods = 0 não entra na varredura de expiração")
    void contractWithoutExpiryIsNotScanned() {
        ContractResponse contract = activeContract();
        UUID openPeriodId = openPeriodOf(contract.id());

        // O período inicial não tem saldo transportado: nada a expirar, independentemente da
        // política. A varredura descarta os dois casos pela mesma consulta.
        assertThat(maintenanceService.findRolloverExpiryDue(100))
                .extracting(MaintenanceTarget::entityId)
                .doesNotContain(openPeriodId);
        assertThat(asOwnerOfA(() -> maintenanceService.expireRollover(openPeriodId))).isFalse();
    }

    private com.devtime.contract.dto.ContractRequests.ContractUpdateRequest updateWithEndDate(
            ContractResponse contract, LocalDate endDate) {
        return new com.devtime.contract.dto.ContractRequests.ContractUpdateRequest(
                contract.name(),
                contract.description(),
                contract.monthlyMinutes(),
                endDate,
                contract.billingDay(),
                contract.rolloverPolicy(),
                contract.rolloverCapMinutes(),
                contract.rolloverExpiryPeriods(),
                contract.overagePolicy(),
                contract.hourlyRate(),
                contract.overageRate(),
                contract.autoRenew(),
                contract.notificationThresholds(),
                contract.defaultCategoryId(),
                null,
                null,
                contract.version());
    }
}
