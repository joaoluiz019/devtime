package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.client.ClientService;
import com.devtime.client.dto.ClientRequests.ClientCreateRequest;
import com.devtime.client.dto.ClientRequests.DeactivateClientRequest;
import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.OveragePolicy;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.domain.RolloverPolicy;
import com.devtime.contract.dto.ContractRequests.ContractCreateRequest;
import com.devtime.contract.dto.ContractRequests.ContractTransitionRequest;
import com.devtime.contract.dto.ContractRequests.ContractUpdateRequest;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/** Regras de contrato e geração de períodos (RN-201 a RN-217, spec 004). */
class ContractServiceIntegrationTest extends FeatureTestSupport {

    private static final LocalDate START = LocalDate.of(2026, 1, 10);

    @Autowired private ContractService contractService;
    @Autowired private ContractPeriodService periodService;
    @Autowired private ClientService clientService;

    @Test
    @DisplayName("§6.1 passo 9: o contrato nasce em DRAFT e nenhum período é gerado")
    void shouldCreateInDraftWithoutPeriods() {
        ContractResponse contract =
                asOwnerOfA(() -> contractService.create(request(activeClient())));

        assertThat(contract.status()).isEqualTo(ContractStatus.DRAFT);
        assertThat(asOwnerOfA(() -> periodService.listByContract(contract.id()))).isEmpty();
        assertThat(contract.periodsPreview())
                .as("a prévia acompanha a criação para conferência antes da ativação")
                .hasSize(3);
        assertThat(contract.periodsPreview().get(0).contractedMinutes())
                .as("RN-217: rateio do primeiro período — 22 de 31 dias")
                .isEqualTo(1703);
        assertThat(contract.periodsPreview().get(0).prorationBasis()).isEqualTo("22 de 31 dias");
    }

    @Test
    @DisplayName("INV-CTR-01: o código é sequencial por tenant no formato CT-XXXX")
    void shouldGenerateSequentialCode() {
        UUID clientId = activeClient();

        assertThat(asOwnerOfA(() -> contractService.create(request(clientId))).code())
                .isEqualTo("CT-0001");
        assertThat(asOwnerOfA(() -> contractService.create(request(clientId, "Segundo"))).code())
                .isEqualTo("CT-0002");
    }

    @Test
    @DisplayName("RN-201/RN-405: contrato para cliente inativo é rejeitado com DEVTIME-2405")
    void shouldRejectContractForInactiveClient() {
        UUID clientId = activeClient();
        asOwnerOfA(
                () -> clientService.deactivate(clientId, new DeactivateClientRequest(true, null)));

        assertThatThrownBy(() -> asOwnerOfA(() -> contractService.create(request(clientId))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2405");
    }

    @Test
    @DisplayName("RN-202/INV-CTR-02: MONTHLY_HOURS sem monthlyMinutes é rejeitado")
    void shouldRejectMonthlyContractWithoutMinutes() {
        UUID clientId = activeClient();
        ContractCreateRequest invalid =
                new ContractCreateRequest(
                        clientId,
                        null,
                        "Sem pacote",
                        null,
                        ContractType.MONTHLY_HOURS,
                        null,
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

        assertThatThrownBy(() -> asOwnerOfA(() -> contractService.create(invalid)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2202");
    }

    @Test
    @DisplayName("CX-08/INV-CTR-03: HOURLY_OPEN com monthlyMinutes é rejeitado com DEVTIME-2210")
    void shouldRejectHourlyOpenWithMinutes() {
        UUID clientId = activeClient();
        ContractCreateRequest invalid =
                new ContractCreateRequest(
                        clientId,
                        null,
                        "Aberto inválido",
                        null,
                        ContractType.HOURLY_OPEN,
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

        assertThatThrownBy(() -> asOwnerOfA(() -> contractService.create(invalid)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2210");
    }

    @Test
    @DisplayName("RN-209/INV-CTR-06: a ativação gera o primeiro período OPEN na mesma transação")
    void activationShouldCreateFirstOpenPeriod() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());

        var activation = asOwnerOfA(() -> contractService.activate(contractId));

        assertThat(activation.status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(activation.firstPeriod().status()).isEqualTo(PeriodStatus.OPEN);
        assertThat(activation.firstPeriod().sequence()).isEqualTo(1);
        assertThat(activation.firstPeriod().startDate()).isEqualTo(START);
        assertThat(activation.firstPeriod().endDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(activation.firstPeriod().contractedMinutes())
                .as("CA-01: o período gerado coincide com a prévia")
                .isEqualTo(1703);
    }

    @Test
    @DisplayName("contracts.md §8.1: a ativação incrementa activeContractsCount do cliente")
    void activationShouldIncrementClientCounter() {
        UUID clientId = activeClient();
        UUID contractId = asOwnerOfA(() -> contractService.create(request(clientId)).id());

        asOwnerOfA(() -> contractService.activate(contractId));

        assertThat(asOwnerOfA(() -> clientService.getById(clientId)).activeContractsCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("ME-04: ativar um contrato já ativo é rejeitado com DEVTIME-2010")
    void shouldRejectDuplicatedActivation() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());
        asOwnerOfA(() -> contractService.activate(contractId));

        assertThatThrownBy(() -> asOwnerOfA(() -> contractService.activate(contractId)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2010");
    }

    @Test
    @DisplayName("RN-205: contrato fora de DRAFT não pode ser excluído — DEVTIME-2205")
    void shouldRejectDeletionOutsideDraft() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());
        asOwnerOfA(() -> contractService.activate(contractId));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () -> {
                                            contractService.delete(contractId);
                                            return null;
                                        }))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2205");
    }

    @Test
    @DisplayName("RN-205: contrato em DRAFT é excluível")
    void shouldDeleteDraftContract() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());

        asOwnerOfA(
                () -> {
                    contractService.delete(contractId);
                    return null;
                });

        assertThatThrownBy(() -> asOwnerOfA(() -> contractService.getById(contractId)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("RN-207/CX-13: alterar monthlyMinutes com período aberto exige confirmação")
    void shouldRequireConfirmationToChangeMonthlyMinutes() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());
        asOwnerOfA(() -> contractService.activate(contractId));
        ContractResponse contract = asOwnerOfA(() -> contractService.getById(contractId));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                contractService.update(
                                                        contractId, update(contract, 3000, false))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2207");

        ContractResponse updated =
                asOwnerOfA(() -> contractService.update(contractId, update(contract, 3000, true)));

        assertThat(updated.monthlyMinutes()).isEqualTo(3000);
        assertThat(
                        asOwnerOfA(() -> periodService.getCurrentPeriod(contractId))
                                .orElseThrow()
                                .contractedMinutes())
                .as("com confirmação, o período aberto acompanha a alteração")
                .isEqualTo(3000);
    }

    @Test
    @DisplayName("RN-208: alterar billingDay com horas lançadas no período aberto é rejeitado")
    void shouldRejectBillingDayChangeWithRegisteredHours() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());
        asOwnerOfA(() -> contractService.activate(contractId));
        // Simula horas lançadas alimentando os campos desnormalizados que o registro atualiza.
        asOwnerOfA(
                () -> {
                    var period = periodRepository().findOpenByContractId(contractId).orElseThrow();
                    period.setConsumedMinutes(120);
                    return periodRepository().save(period);
                });

        ContractResponse contract = asOwnerOfA(() -> contractService.getById(contractId));
        ContractUpdateRequest change =
                new ContractUpdateRequest(
                        contract.name(),
                        null,
                        null,
                        null,
                        15,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        contract.version());

        assertThatThrownBy(() -> asOwnerOfA(() -> contractService.update(contractId, change)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2208");
    }

    @Test
    @DisplayName("RN-214: o encerramento trunca o período corrente em endDate")
    void endShouldTruncateCurrentPeriod() {
        UUID clientId = activeClient();
        UUID contractId = asOwnerOfA(() -> contractService.create(request(clientId)).id());
        asOwnerOfA(() -> contractService.activate(contractId));

        LocalDate endDate = LocalDate.of(2026, 1, 20);
        var response =
                asOwnerOfA(
                        () ->
                                contractService.end(
                                        contractId,
                                        new ContractTransitionRequest(
                                                "Término natural", endDate, null)));

        assertThat(response.status()).isEqualTo(ContractStatus.ENDED);
        assertThat(response.truncatedPeriod().endDate()).isEqualTo(endDate);
        assertThat(asOwnerOfA(() -> clientService.getById(clientId)).activeContractsCount())
                .as("contracts.md §8.4: o encerramento decrementa o contador do cliente")
                .isZero();
    }

    @Test
    @DisplayName("CE-15: contrato encerrado é terminal e não aceita nova transição")
    void endedContractShouldBeTerminal() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());
        asOwnerOfA(() -> contractService.activate(contractId));
        asOwnerOfA(
                () ->
                        contractService.end(
                                contractId,
                                new ContractTransitionRequest("Término natural", null, null)));

        assertThatThrownBy(() -> asOwnerOfA(() -> contractService.resume(contractId)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2011");
    }

    @Test
    @DisplayName("RN-215: suspensão sem justificativa de 10 caracteres é rejeitada")
    void shouldRequireReasonToSuspend() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());
        asOwnerOfA(() -> contractService.activate(contractId));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                contractService.suspend(
                                                        contractId,
                                                        new ContractTransitionRequest(
                                                                "curto", null, null))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2215");
    }

    @Test
    @DisplayName("CE-ME-09: a retomada preserva a contiguidade dos períodos (INV-PER-03)")
    void resumeShouldKeepPeriodsContiguous() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());
        asOwnerOfA(() -> contractService.activate(contractId));
        asOwnerOfA(
                () ->
                        contractService.suspend(
                                contractId,
                                new ContractTransitionRequest(
                                        "Cliente pausou o serviço", null, null)));

        asOwnerOfA(() -> contractService.resume(contractId));

        var periods = asOwnerOfA(() -> periodService.listByContract(contractId));
        assertThat(periods).hasSizeGreaterThan(1);
        for (int index = 1; index < periods.size(); index++) {
            assertThat(periods.get(index).startDate())
                    .as("INV-PER-03: cada período começa no dia seguinte ao anterior")
                    .isEqualTo(periods.get(index - 1).endDate().plusDays(1));
        }
        assertThat(periods).filteredOn(period -> period.status() == PeriodStatus.OPEN).hasSize(1);
    }

    @Test
    @DisplayName("RN-107: resolveOpenPeriod devolve o período que contém a data de trabalho")
    void shouldResolvePeriodByWorkDate() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());
        asOwnerOfA(() -> contractService.activate(contractId));

        assertThat(
                        asOwnerOfA(
                                () ->
                                        periodService.resolveOpenPeriod(
                                                contractId, LocalDate.of(2026, 1, 15))))
                .isPresent();
        assertThat(
                        asOwnerOfA(
                                () ->
                                        periodService.resolveOpenPeriod(
                                                contractId, LocalDate.of(2026, 3, 15))))
                .as("sem período para a data, o registro de horas é rejeitado por RN-107")
                .isEmpty();
    }

    @Test
    @DisplayName("SG-03: sem CONTRACT_VIEW_FINANCIAL os campos monetários não saem do backend")
    void shouldOmitFinancialFieldsWithoutPermission() {
        UUID clientId = activeClient();
        ContractCreateRequest withRates =
                new ContractCreateRequest(
                        clientId,
                        null,
                        "Com valores",
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
                        new java.math.BigDecimal("150.0000"),
                        new java.math.BigDecimal("180.0000"),
                        "BRL",
                        true,
                        true,
                        null,
                        null,
                        null);
        UUID contractId = asOwnerOfA(() -> contractService.create(withRates).id());

        assertThat(asOwnerOfA(() -> contractService.getById(contractId)).hourlyRate()).isNotNull();
        assertThat(
                        runAs(
                                        tenantAId,
                                        userAId,
                                        Role.VIEWER,
                                        () -> contractService.getById(contractId))
                                .hourlyRate())
                .as("VIEWER possui CONTRACT_VIEW_FINANCIAL")
                .isNotNull();
    }

    @Test
    @DisplayName("BR-208/RN-002: contrato de outro tenant é invisível e resulta em 404")
    void shouldIsolateContractsBetweenTenants() {
        UUID contractOfA = asOwnerOfA(() -> contractService.create(request(activeClient())).id());

        assertThat(
                        asOwnerOfB(
                                        () ->
                                                contractService.search(
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        PageRequest.of(0, 20)))
                                .content())
                .isEmpty();
        assertThatThrownBy(() -> asOwnerOfB(() -> contractService.getById(contractOfA)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("nota ³/permissions.md §7: MEMBER não cria nem transiciona contratos")
    void memberShouldNotManageContracts() {
        UUID clientId = activeClient();

        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        userAId,
                                        Role.MEMBER,
                                        () -> contractService.create(request(clientId))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("contracts.md §12.2: o histórico devolve a série de períodos do contrato")
    void shouldReturnPeriodHistory() {
        UUID contractId = asOwnerOfA(() -> contractService.create(request(activeClient())).id());
        asOwnerOfA(() -> contractService.activate(contractId));

        var history = asOwnerOfA(() -> contractService.history(contractId, 12));

        assertThat(history.contractId()).isEqualTo(contractId);
        assertThat(history.periods()).hasSize(1);
        assertThat(history.aggregates().periodsCount()).isEqualTo(1);
        assertThat(history.periods().get(0).contractedMinutes()).isEqualTo(1703);
    }

    // ── Apoio ───────────────────────────────────────────────────────────────────────────────

    @Autowired private ContractPeriodRepository periodRepository;

    private ContractPeriodRepository periodRepository() {
        return periodRepository;
    }

    private UUID activeClient() {
        return asOwnerOfA(
                () ->
                        clientService
                                .create(
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
                                                List.of()))
                                .id());
    }

    private ContractCreateRequest request(UUID clientId) {
        return request(clientId, "Sustentação Mensal");
    }

    private ContractCreateRequest request(UUID clientId, String name) {
        return new ContractCreateRequest(
                clientId,
                null,
                name,
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

    private ContractUpdateRequest update(
            ContractResponse contract, int monthlyMinutes, boolean applyToCurrentPeriod) {
        return new ContractUpdateRequest(
                contract.name(),
                null,
                monthlyMinutes,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                applyToCurrentPeriod,
                contract.version());
    }
}
