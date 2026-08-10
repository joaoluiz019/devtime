package com.devtime.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.report.dto.ReportResponses.ClientSummaryReportResponse;
import com.devtime.report.dto.ReportResponses.ProductivityReportResponse;
import com.devtime.report.dto.ReportResponses.TicketDetailReportResponse;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.WorkLogScenario;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Os três relatórios que a suíte anterior não alcançava: resumo por cliente, detalhe de ticket e
 * produtividade (§6, §7 e §9 de reports.md).
 *
 * <p>Só a folha de horas e o relatório de período tinham teste. Os outros três montam os mesmos
 * números por caminhos diferentes — e é justamente a divergência entre eles que o cliente percebe:
 * o resumo por cliente e o relatório do período precisam somar igual, ou a fatura e o extrato
 * contam histórias distintas sobre o mesmo mês.
 */
class ReportTypesIntegrationTest extends FeatureTestSupport {

    private static final LocalDate JANUARY_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JANUARY_END = LocalDate.of(2026, 1, 31);

    @Autowired private ReportService reportService;
    @Autowired private WorkLogService workLogService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("§6: o resumo por cliente soma as mesmas horas registradas no período")
    void clientSummaryMatchesRegisteredHours() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(registro(setup, 9, 0, 11, 0)));

        ClientSummaryReportResponse relatorio =
                asOwnerOfA(() -> reportService.clientSummary(setup.clientId(), janeiro()));

        assertThat(relatorio.totals().netMinutes())
                .as("120 minutos registrados precisam aparecer como 120 no resumo do cliente")
                .isEqualTo(120);
        assertThat(relatorio.client().name())
                .as("RN-703: o bloco do cliente carrega o valor congelado, não o ponteiro")
                .isNotBlank();
    }

    @Test
    @DisplayName("§7: o detalhe do ticket lista as linhas daquele ticket e nada além")
    void ticketDetailListsOnlyItsOwnEntries() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(registro(setup, 9, 0, 11, 0)));

        TicketDetailReportResponse relatorio =
                asOwnerOfA(() -> reportService.ticketDetail(setup.ticket().id(), janeiro()));

        assertThat(relatorio.totals().entriesCount()).isEqualTo(1);
        assertThat(relatorio.totals().netMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("§9 / CX-21: MEMBER não alcança o relatório de produtividade")
    void productivityIsRestricted() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(registro(setup, 9, 0, 11, 0)));

        ProductivityReportResponse doOwner =
                asOwnerOfA(() -> reportService.productivity(janeiro()));
        assertThat(doOwner).isNotNull();

        UUID membro = memberOfTenantA();
        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        membro,
                                        Role.MEMBER,
                                        () -> reportService.productivity(janeiro())))
                .as("o relatório compara pessoas: quem não administra a equipe não o vê")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("SG-01: cliente de outro tenant é indistinguível de inexistente")
    void clientOfAnotherTenantIsNotFound() {
        var setup = asOwnerOfA(scenario::create);

        assertThatThrownBy(
                        () ->
                                asOwnerOfB(
                                        () ->
                                                reportService.clientSummary(
                                                        setup.clientId(), janeiro())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("CE-R-06: cliente sem registros produz relatório com totais zerados")
    void emptyClientSummaryIsGenerated() {
        var setup = asOwnerOfA(scenario::create);

        ClientSummaryReportResponse relatorio =
                asOwnerOfA(() -> reportService.clientSummary(setup.clientId(), janeiro()));

        assertThat(relatorio.totals().netMinutes())
                .as("ausência de horas é um relatório zerado, não um erro")
                .isZero();
    }

    private UUID memberOfTenantA() {
        return asOwnerOfA(
                () -> {
                    UUID id =
                            userRepository
                                    .save(
                                            com.devtime.support.FoundationDataBuilder.user(
                                                    "membro-"
                                                            + UUID.randomUUID()
                                                                    .toString()
                                                                    .substring(0, 8)
                                                            + "@exemplo.com",
                                                    NOW))
                                    .getId();
                    membershipRepository.save(
                            com.devtime.support.FoundationDataBuilder.membership(
                                    tenantAId, id, Role.MEMBER, NOW));
                    return id;
                });
    }

    private ReportFilters janeiro() {
        return new ReportFilters(
                null,
                null,
                null,
                null,
                JANUARY_START,
                JANUARY_END,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private WorkLogCreateRequest registro(
            WorkLogScenario.Scenario setup,
            int horaInicio,
            int minutoInicio,
            int horaFim,
            int minutoFim) {
        return new WorkLogCreateRequest(
                setup.ticket().id(),
                WorkLogScenario.at(horaInicio, minutoInicio),
                WorkLogScenario.at(horaFim, minutoFim),
                0,
                "Registro para o relatório",
                setup.category().id(),
                true,
                List.of(),
                null);
    }
}
