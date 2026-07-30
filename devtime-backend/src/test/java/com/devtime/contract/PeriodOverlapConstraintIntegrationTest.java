package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.client.ClientService;
import com.devtime.client.dto.ClientRequests.ClientCreateRequest;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.OveragePolicy;
import com.devtime.contract.domain.RolloverPolicy;
import com.devtime.contract.dto.ContractRequests.ContractCreateRequest;
import com.devtime.support.FeatureTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Constraints estruturais de {@code contract_periods} (T-004-49).
 *
 * <p>Os {@code INSERT} são feitos por SQL direto, <b>contornando a aplicação</b> de propósito: o
 * ponto do teste é provar que a integridade não depende do código de negócio. Se a garantia
 * estivesse apenas no serviço, um job, uma migração de dados ou um erro futuro poderiam produzir
 * períodos sobrepostos — e horas alocadas no período errado (INV-PER-02).
 */
class PeriodOverlapConstraintIntegrationTest extends FeatureTestSupport {

    @Autowired private ContractService contractService;
    @Autowired private ClientService clientService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("INV-PER-02: a constraint EXCLUDE impede sobreposição mesmo por INSERT direto")
    void shouldRejectOverlappingPeriodsFromDirectInsert() {
        UUID contractId = activeContract();

        assertThatThrownBy(
                        () ->
                                insertPeriod(
                                        contractId,
                                        2,
                                        LocalDate.of(2026, 1, 20),
                                        LocalDate.of(2026, 2, 20),
                                        "SCHEDULED"))
                .as("o período 20/01–20/02 sobrepõe o período aberto 10/01–31/01")
                .hasMessageContaining("ex_periods_no_overlap");
    }

    @Test
    @DisplayName("INV-PER-03: períodos que apenas se tocam são aceitos")
    void shouldAcceptAdjacentPeriods() {
        UUID contractId = activeContract();

        insertPeriod(
                contractId, 2, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), "SCHEDULED");

        assertThat(countPeriods(contractId)).isEqualTo(2);
    }

    @Test
    @DisplayName("INV-PER-07: o índice parcial garante no máximo um período OPEN por contrato")
    void shouldRejectSecondOpenPeriod() {
        UUID contractId = activeContract();

        assertThatThrownBy(
                        () ->
                                insertPeriod(
                                        contractId,
                                        2,
                                        LocalDate.of(2026, 2, 1),
                                        LocalDate.of(2026, 2, 28),
                                        "OPEN"))
                .hasMessageContaining("uq_periods_single_open");
    }

    @Test
    @DisplayName("INV-PER-01: (contractId, sequence) é único")
    void shouldRejectDuplicatedSequence() {
        UUID contractId = activeContract();

        assertThatThrownBy(
                        () ->
                                insertPeriod(
                                        contractId,
                                        1,
                                        LocalDate.of(2026, 3, 1),
                                        LocalDate.of(2026, 3, 31),
                                        "SCHEDULED"))
                .hasMessageContaining("uq_periods_contract_sequence");
    }

    private UUID activeContract() {
        UUID clientId =
                asOwnerOfA(
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
        UUID contractId =
                asOwnerOfA(
                        () ->
                                contractService
                                        .create(
                                                new ContractCreateRequest(
                                                        clientId,
                                                        null,
                                                        "Contrato",
                                                        null,
                                                        ContractType.MONTHLY_HOURS,
                                                        2400,
                                                        LocalDate.of(2026, 1, 10),
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
                                                        null))
                                        .id());
        asOwnerOfA(() -> contractService.activate(contractId));
        return contractId;
    }

    /**
     * {@code INSERT} direto, deliberadamente fora do caminho da aplicação.
     *
     * <p>A escrita é envolvida em transação porque o pool está configurado com {@code auto-commit:
     * false} (application.yml): sem uma transação explícita, o comando executaria e jamais seria
     * confirmado — e o teste passaria a verificar nada.
     */
    private void insertPeriod(
            UUID contractId, int sequence, LocalDate start, LocalDate end, String status) {
        inTransaction(() -> insertPeriodStatement(contractId, sequence, start, end, status));
    }

    private int insertPeriodStatement(
            UUID contractId, int sequence, LocalDate start, LocalDate end, String status) {
        return jdbcTemplate.update(
                """
                INSERT INTO contract_periods
                    (id, tenant_id, contract_id, sequence, label, start_date, end_date, status,
                     contracted_minutes, currency, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'BRL', now(), now(), 0)
                """,
                UUID.randomUUID(),
                tenantAId,
                contractId,
                sequence,
                "teste",
                java.sql.Date.valueOf(start),
                java.sql.Date.valueOf(end),
                status);
    }

    private int countPeriods(UUID contractId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM contract_periods WHERE contract_id = ? AND deleted_at IS NULL",
                        Integer.class,
                        contractId);
        return count == null ? 0 : count;
    }
}
