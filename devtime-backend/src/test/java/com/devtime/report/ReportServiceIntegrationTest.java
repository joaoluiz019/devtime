package com.devtime.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.BalanceService;
import com.devtime.report.domain.ReportGrouping;
import com.devtime.report.domain.ReportSource;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.report.dto.ReportResponses.ContractPeriodReportResponse;
import com.devtime.report.dto.ReportResponses.TimesheetReportResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.error.ErrorCode;
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
 * Relatórios contra o banco real (§33 de specs/012).
 *
 * <p>Cobre as três propriedades que só o banco revela: os números do relatório são <b>os mesmos</b>
 * de {@code BalanceService} (RP-03), o período de outro tenant é indistinguível de inexistente
 * (SG-01, CA-21) e o escopo de {@code MEMBER} entra na consulta, não em um filtro posterior
 * (INV-RPT-04).
 *
 * <p>O intervalo usado é janeiro de 2026 porque o cenário compartilhado ativa contratos em
 * 10/01/2026, enquanto o relógio dos testes está fixo em 29/07/2026.
 */
class ReportServiceIntegrationTest extends FeatureTestSupport {

    private static final LocalDate JANUARY_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JANUARY_END = LocalDate.of(2026, 1, 31);

    @Autowired private ReportService reportService;
    @Autowired private BalanceService balanceService;
    @Autowired private WorkLogService workLogService;
    @Autowired private WorkLogScenario scenario;

    private ReportFilters filters(ReportGrouping groupBy) {
        return new ReportFilters(
                groupBy, null, null, null, null, null, null, null, null, null, null, null);
    }

    private ReportFilters januaryFilters() {
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

    private WorkLogCreateRequest workLog(WorkLogScenario.Scenario setup, int fromHour, int toHour) {
        return new WorkLogCreateRequest(
                setup.ticket().id(),
                WorkLogScenario.at(fromHour, 0),
                WorkLogScenario.at(toHour, 0),
                null,
                "Registro do relatório",
                setup.category().id(),
                true,
                List.of(),
                null);
    }

    @Test
    @DisplayName("RN-702 / CA-03: período aberto é servido ao vivo e marcado como parcial")
    void openPeriodIsLiveAndPartial() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(workLog(setup, 9, 12)));

        ContractPeriodReportResponse report =
                asOwnerOfA(
                        () ->
                                reportService.contractPeriod(
                                        setup.period().id(), filters(ReportGrouping.DATE)));

        assertThat(report.source()).isEqualTo(ReportSource.LIVE);
        assertThat(report.isPartial()).isTrue();
        assertThat(report.snapshotAt()).as("ao vivo não existe instante de congelamento").isNull();
        assertThat(report.issueId()).startsWith("EM-");
    }

    @Test
    @DisplayName("RP-03: o saldo do relatório é idêntico ao que 011 calcula")
    void balanceMatchesBankHours() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(workLog(setup, 9, 12)));

        ContractPeriodReportResponse report =
                asOwnerOfA(
                        () ->
                                reportService.contractPeriod(
                                        setup.period().id(), filters(ReportGrouping.DATE)));
        var balance = asOwnerOfA(() -> balanceService.getBalance(setup.period().id()));

        // Uma segunda implementação da fórmula divergiria da primeira na próxima mudança de regra,
        // e o relatório é onde a divergência seria descoberta por um cliente, não por um teste.
        assertThat(report.balance().consumedMinutes()).isEqualTo(balance.consumedMinutes());
        assertThat(report.balance().availableMinutes()).isEqualTo(balance.availableMinutes());
        assertThat(report.balance().remainingMinutes()).isEqualTo(balance.remainingMinutes());
    }

    @Test
    @DisplayName("RN-704: o detalhamento soma exatamente as linhas impressas")
    void totalsMatchPrintedEntries() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(workLog(setup, 9, 12)));

        ContractPeriodReportResponse report =
                asOwnerOfA(
                        () ->
                                reportService.contractPeriod(
                                        setup.period().id(), filters(ReportGrouping.DATE)));

        int printed =
                report.groups().stream()
                        .flatMap(group -> group.entries().stream())
                        .mapToInt(entry -> entry.netMinutes())
                        .sum();
        assertThat(report.totals().netMinutes()).isEqualTo(printed);
        assertThat(report.totals().durationLabel()).isEqualTo("03:00");
    }

    @Test
    @DisplayName("SG-01 / CA-21: período de outro tenant responde 404, nunca 403")
    void otherTenantPeriodIsNotFound() {
        var setup = asOwnerOfA(scenario::create);
        UUID periodOfA = setup.period().id();

        assertThatThrownBy(
                        () ->
                                asOwnerOfB(
                                        () ->
                                                reportService.contractPeriod(
                                                        periodOfA, filters(ReportGrouping.DATE))))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("RN-711 / INV-RPT-04: MEMBER não vê na folha de horas o registro de terceiro")
    void memberDoesNotSeeForeignEntries() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(workLog(setup, 9, 12)));

        // O registro pertence ao OWNER; o MEMBER é outro usuário do mesmo tenant.
        TimesheetReportResponse asMember =
                runAs(
                        tenantAId,
                        UUID.randomUUID(),
                        Role.MEMBER,
                        () -> reportService.timesheet(januaryFilters()));

        assertThat(asMember.totals().entriesCount())
                .as("o escopo entra na consulta, não em um filtro aplicado depois")
                .isZero();
    }

    @Test
    @DisplayName("CX-21: MEMBER não acessa o relatório de produtividade")
    void memberCannotAccessProductivity() {
        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        UUID.randomUUID(),
                                        Role.MEMBER,
                                        () -> reportService.productivity(januaryFilters())))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("RN-705 / CX-14 / DEVTIME-3001: 367 dias é recusado")
    void rangeAboveLimitIsRejected() {
        ReportFilters tooLong =
                new ReportFilters(
                        null,
                        null,
                        null,
                        null,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2027, 1, 2),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> asOwnerOfA(() -> reportService.timesheet(tooLong)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).getErrorCode())
                .isEqualTo(ErrorCode.DATE_RANGE_EXCEEDED);
    }

    @Test
    @DisplayName("CE-R-06 / CX-08: relatório sem registros é gerado com totais zerados")
    void emptyReportIsGeneratedNormally() {
        var setup = asOwnerOfA(scenario::create);

        ContractPeriodReportResponse report =
                asOwnerOfA(
                        () ->
                                reportService.contractPeriod(
                                        setup.period().id(), filters(ReportGrouping.DATE)));

        assertThat(report.totals().entriesCount()).isZero();
        assertThat(report.totals().durationLabel()).isEqualTo("00:00");
    }
}
