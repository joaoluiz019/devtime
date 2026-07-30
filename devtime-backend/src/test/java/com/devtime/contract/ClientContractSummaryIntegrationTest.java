package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.client.ClientService;
import com.devtime.client.dto.ClientRequests.ClientCreateRequest;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.OveragePolicy;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.domain.RolloverPolicy;
import com.devtime.contract.dto.ContractRequests.ContractCreateRequest;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.support.FeatureTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Consultas agregadas de período e resumo por cliente (clients.md §8, contracts.md §12). */
class ClientContractSummaryIntegrationTest extends FeatureTestSupport {

    private static final LocalDate START = LocalDate.of(2026, 1, 10);

    @Autowired private ContractService contractService;
    @Autowired private ContractPeriodService periodService;
    @Autowired private ClientContractSummaryService summaryService;
    @Autowired private ClientService clientService;

    @Test
    @DisplayName("clients.md §8: o resumo consolida os períodos dos contratos do cliente")
    void summaryShouldConsolidateContractPeriods() {
        UUID clientId = client();
        UUID contractId = activeContract(clientId);

        var summary = asOwnerOfA(() -> summaryService.summarize(clientId, 6));

        assertThat(summary.clientId()).isEqualTo(clientId);
        assertThat(summary.currency()).isEqualTo("BRL");
        assertThat(summary.totals().contractedMinutes()).isEqualTo(1703);
        assertThat(summary.totals().consumedMinutes()).isZero();
        assertThat(summary.totals().remainingMinutes()).isEqualTo(1703);
        assertThat(summary.history()).hasSize(1);
        assertThat(summary.byContract()).hasSize(1);
        assertThat(summary.byContract().get(0).contractId()).isEqualTo(contractId);
    }

    @Test
    @DisplayName("SM-03: o histórico é ordenado do período mais antigo ao mais recente")
    void summaryHistoryShouldBeChronological() {
        UUID clientId = client();
        UUID contractId = activeContract(clientId);
        asOwnerOfA(() -> contractService.suspend(contractId, transition()));
        asOwnerOfA(() -> contractService.resume(contractId));

        var history = asOwnerOfA(() -> summaryService.summarize(clientId, 24)).history();

        assertThat(history).hasSizeGreaterThan(1);
        assertThat(history)
                .isSortedAccordingTo(
                        java.util.Comparator.comparing(
                                com.devtime.contract.dto.ContractResponses.ContractHistoryPeriod
                                        ::label));
    }

    @Test
    @DisplayName("CE-C-06: cliente sem contratos devolve resumo vazio, não erro")
    void summaryShouldBeEmptyWithoutContracts() {
        UUID clientId = client();

        var summary = asOwnerOfA(() -> summaryService.summarize(clientId, 6));

        assertThat(summary.byContract()).isEmpty();
        assertThat(summary.history()).isEmpty();
        assertThat(summary.currency()).isNull();
        assertThat(summary.totals().contractedMinutes()).isZero();
    }

    @Test
    @DisplayName("spec §22.2: listByContract e getById devolvem os períodos do contrato")
    void periodQueriesShouldReturnContractPeriods() {
        UUID contractId = activeContract(client());

        var periods = asOwnerOfA(() -> periodService.listByContract(contractId));

        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).status()).isEqualTo(PeriodStatus.OPEN);

        var byId = asOwnerOfA(() -> periodService.getById(periods.get(0).id()));
        assertThat(byId.sequence()).isEqualTo(1);
        assertThat(byId.currency()).isEqualTo("BRL");
    }

    @Test
    @DisplayName("RN-002: período de outro tenant resulta em 404")
    void periodOfAnotherTenantShouldNotBeVisible() {
        UUID contractId = activeContract(client());
        UUID periodId = asOwnerOfA(() -> periodService.listByContract(contractId)).get(0).id();

        assertThatThrownBy(() -> asOwnerOfB(() -> periodService.getById(periodId)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("spec §22.2: getCurrentPeriod devolve o período aberto do contrato")
    void currentPeriodShouldBeTheOpenOne() {
        UUID contractId = activeContract(client());

        assertThat(asOwnerOfA(() -> periodService.getCurrentPeriod(contractId)))
                .isPresent()
                .get()
                .satisfies(period -> assertThat(period.status()).isEqualTo(PeriodStatus.OPEN));
    }

    @Test
    @DisplayName("RN-306: contrato em DRAFT não é aceito para registro de horas")
    void draftContractShouldNotAcceptWorkLogs() {
        UUID contractId = asOwnerOfA(() -> contractService.create(contractRequest(client())).id());

        assertThatThrownBy(() -> asOwnerOfA(() -> contractService.getActiveForWorkLog(contractId)))
                .isInstanceOf(com.devtime.shared.error.BusinessRuleException.class);
    }

    @Test
    @DisplayName("RN-306: contrato ACTIVE é aceito para registro de horas")
    void activeContractShouldAcceptWorkLogs() {
        UUID contractId = activeContract(client());

        assertThat(asOwnerOfA(() -> contractService.getActiveForWorkLog(contractId)).id())
                .isEqualTo(contractId);
    }

    @Test
    @DisplayName("contracts.md §5: código informado explicitamente é preservado")
    void explicitCodeShouldBePreserved() {
        UUID clientId = client();
        ContractCreateRequest withCode =
                new ContractCreateRequest(
                        clientId,
                        "CT-9999",
                        "Com código",
                        null,
                        ContractType.MONTHLY_HOURS,
                        2400,
                        START,
                        null,
                        1,
                        RolloverPolicy.NONE,
                        null,
                        1,
                        OveragePolicy.WARN,
                        null,
                        null,
                        "BRL",
                        true,
                        true,
                        null,
                        null,
                        null);

        assertThat(asOwnerOfA(() -> contractService.create(withCode)).code()).isEqualTo("CT-9999");
    }

    @Test
    @DisplayName("DEVTIME-2206: código já usado no tenant é rejeitado")
    void duplicatedExplicitCodeShouldBeRejected() {
        UUID clientId = client();
        ContractCreateRequest withCode =
                new ContractCreateRequest(
                        clientId,
                        "CT-5000",
                        "Primeiro",
                        null,
                        ContractType.MONTHLY_HOURS,
                        2400,
                        START,
                        null,
                        1,
                        RolloverPolicy.NONE,
                        null,
                        1,
                        OveragePolicy.WARN,
                        null,
                        null,
                        "BRL",
                        true,
                        true,
                        null,
                        null,
                        null);
        asOwnerOfA(() -> contractService.create(withCode));

        ContractCreateRequest duplicated =
                new ContractCreateRequest(
                        clientId,
                        "CT-5000",
                        "Segundo",
                        null,
                        ContractType.MONTHLY_HOURS,
                        2400,
                        START,
                        null,
                        1,
                        RolloverPolicy.NONE,
                        null,
                        1,
                        OveragePolicy.WARN,
                        null,
                        null,
                        "BRL",
                        true,
                        true,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> asOwnerOfA(() -> contractService.create(duplicated)))
                .isInstanceOf(com.devtime.shared.error.BusinessRuleException.class)
                .extracting(
                        failure ->
                                ((com.devtime.shared.error.BusinessRuleException) failure)
                                        .getErrorCode()
                                        .getCode())
                .isEqualTo("DEVTIME-2206");
    }

    private com.devtime.contract.dto.ContractRequests.ContractTransitionRequest transition() {
        return new com.devtime.contract.dto.ContractRequests.ContractTransitionRequest(
                "Cliente pausou o serviço", null, null);
    }

    private UUID client() {
        return asOwnerOfA(
                        () ->
                                clientService.create(
                                        new ClientCreateRequest(
                                                "Cliente " + UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                List.of())))
                .id();
    }

    private UUID activeContract(UUID clientId) {
        UUID contractId = asOwnerOfA(() -> contractService.create(contractRequest(clientId)).id());
        asOwnerOfA(() -> contractService.activate(contractId));
        return contractId;
    }

    private ContractCreateRequest contractRequest(UUID clientId) {
        return new ContractCreateRequest(
                clientId,
                null,
                "Contrato",
                null,
                ContractType.MONTHLY_HOURS,
                2400,
                START,
                null,
                1,
                RolloverPolicy.NONE,
                null,
                1,
                OveragePolicy.WARN,
                null,
                null,
                "BRL",
                true,
                true,
                null,
                null,
                null);
    }
}
