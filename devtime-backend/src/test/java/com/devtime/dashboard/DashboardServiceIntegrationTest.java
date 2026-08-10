package com.devtime.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.BalanceService;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.dashboard.domain.ContractSeverity;
import com.devtime.dashboard.domain.DashboardExceptions;
import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.dashboard.domain.DashboardScope;
import com.devtime.dashboard.dto.DashboardResponses.ChartPointDto;
import com.devtime.dashboard.dto.DashboardResponses.ContractStatusDto;
import com.devtime.dashboard.dto.DashboardResponses.DashboardResponse;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.FoundationDataBuilder;
import com.devtime.support.WorkLogScenario;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Painel contra o banco real (specs/010 §33).
 *
 * <p>O teste que justifica a suíte é {@link #balanceMatchesBankHours()} — T-010-02, escrito antes
 * do código. Ele prova INV-DSH-01: o número que o painel exibe é <b>o mesmo</b> que {@code
 * BalanceService} devolve. R-02 classifica a divergência como impacto alto, e SQ-10 determina que
 * uma divergência de saldo reportada bloqueia toda a fila de desenvolvimento.
 *
 * <p>O intervalo consultado é {@code CUSTOM} sobre janeiro de 2026 porque o cenário compartilhado
 * ativa contratos em 10/01/2026, enquanto o relógio dos testes está fixo em 29/07/2026.
 */
class DashboardServiceIntegrationTest extends FeatureTestSupport {

    private static final LocalDate JANUARY_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JANUARY_END = LocalDate.of(2026, 1, 31);

    @Autowired private DashboardService dashboardService;
    @Autowired private BalanceService balanceService;
    @Autowired private WorkLogService workLogService;
    @Autowired private WorkLogScenario scenario;
    @Autowired private com.devtime.support.TicketScenario ticketScenario;
    @Autowired private DashboardChartCache chartCache;

    /**
     * Primeiro dia livre depois de {@code WORK_DAY} (15/01), compartilhado por {@link #consume}.
     */
    private int proximoDiaLivre = 16;

    private DashboardResponse loadJanuary() {
        return dashboardService.load(DashboardPeriodType.CUSTOM, JANUARY_START, JANUARY_END);
    }

    // ── Equivalência com 011 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("INV-DSH-01 / CA-01 / T-010-02: o saldo do painel é idêntico ao de 011")
    void balanceMatchesBankHours() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 17, 0)));

        DashboardResponse dashboard = asOwnerOfA(this::loadJanuary);
        ContractStatusDto card =
                dashboard.contracts().stream()
                        .filter(item -> item.contractId().equals(setup.contract().id()))
                        .findFirst()
                        .orElseThrow();
        PeriodBalanceResponse balance =
                asOwnerOfA(() -> balanceService.getBalance(setup.period().id()));

        assertThat(card.periodId()).isEqualTo(balance.periodId());
        assertThat(card.availableMinutes()).isEqualTo(balance.availableMinutes());
        assertThat(card.consumedMinutes()).isEqualTo(balance.consumedMinutes());
        assertThat(card.remainingMinutes()).isEqualTo(balance.remainingMinutes());
        assertThat(card.consumptionRate())
                .as("nenhum recálculo: a taxa vem inteira de BalanceService")
                .isEqualByComparingTo(balance.consumptionRate());
        assertThat(card.isPartial())
                .as("RN-702: período aberto produz número em evolução")
                .isEqualTo(balance.isPartial());
    }

    // ── Ordenação por criticidade ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP-02 / CA-02: contratos ordenados por severidade decrescente")
    void contractsOrderedByCriticality() {
        var lowUsage = asOwnerOfA(scenario::create);
        var highUsage =
                asOwnerOfA(
                        () ->
                                scenario.withContract(
                                        lowUsage.clientId(),
                                        ticketScenario.activeContract(lowUsage.clientId())));

        // 20% do saldo do período no primeiro contrato e 85% no segundo — a faixa de WARNING de
        // §6.1 vai de 80% a 100%.
        consume(lowUsage, 0.20);
        consume(highUsage, 0.85);

        List<ContractStatusDto> contracts = asOwnerOfA(this::loadJanuary).contracts();

        assertThat(contracts).hasSizeGreaterThanOrEqualTo(2);
        assertThat(contracts.get(0).contractId())
                .as("o que exige ação hoje fica no topo")
                .isEqualTo(highUsage.contract().id());
        assertThat(contracts.get(0).severity()).isEqualTo(ContractSeverity.WARNING);
        assertThat(contracts)
                .extracting(ContractStatusDto::severity)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("CA-04 / FA-13: o alerta de consumo acompanha o estado presente do contrato")
    void alertsDeriveFromCurrentState() {
        var setup = asOwnerOfA(scenario::create);

        assertThat(asOwnerOfA(this::loadJanuary).alerts())
                .as("sem consumo relevante não existe alerta a resolver")
                .noneMatch(alert -> alert.type().startsWith("CONTRACT_USAGE"));

        consume(setup, 0.85);

        assertThat(asOwnerOfA(this::loadJanuary).alerts())
                .as("RN-603: o tipo carrega o limiar do contrato, como o dedupeKey da notificação")
                .anyMatch(alert -> alert.type().equals("CONTRACT_USAGE_80"));
    }

    // ── Escopo de dados ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP-01 / CA-08: papel com DASHBOARD_VIEW_ANY recebe escopo TENANT")
    void ownerGetsTenantScope() {
        asOwnerOfA(scenario::create);

        assertThat(asOwnerOfA(this::loadJanuary).scope()).isEqualTo(DashboardScope.TENANT);
    }

    @Test
    @DisplayName("CP-01 / CA-08 / CX-12: MEMBER recebe escopo USER e nenhum dado de colega")
    void memberGetsUserScopeAndNoColleagueData() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 17, 0)));
        UUID memberId = memberOfTenantA();

        DashboardResponse dashboard = runAs(tenantAId, memberId, Role.MEMBER, this::loadJanuary);

        assertThat(dashboard.scope()).isEqualTo(DashboardScope.USER);
        assertThat(dashboard.quickStats().periodMinutes())
                .as("SG-02: o total do membro não pode revelar as horas do dono por subtração")
                .isZero();
        assertThat(dashboard.contracts())
                .as("nota ²: sem vínculo, nenhum contrato é visível")
                .isEmpty();
        assertThat(dashboard.recentWorkLogs()).isEmpty();
    }

    // ── Isolamento entre tenants ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CA-18 / SG-01: nenhum dado do tenant B aparece no painel do tenant A")
    void tenantIsolation() {
        var inA = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(inA, 9, 0, 17, 0)));
        var inB = asOwnerOfB(scenario::create);
        asOwnerOfB(() -> workLogService.create(request(inB, 9, 0, 17, 0)));

        DashboardResponse dashboardOfA = asOwnerOfA(this::loadJanuary);

        assertThat(dashboardOfA.contracts())
                .extracting(ContractStatusDto::contractId)
                .doesNotContain(inB.contract().id());
        assertThat(dashboardOfA.quickStats().periodMinutes())
                .as("as horas dos dois tenants não se somam")
                .isEqualTo(480);
    }

    @Test
    @DisplayName("SG-07 / CA-15 / R-05: o cache de gráficos nunca serve dado de outro tenant")
    void chartCacheIsIsolatedByTenant() {
        var inA = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(inA, 9, 0, 17, 0)));
        asOwnerOfB(scenario::create);

        List<ChartPointDto> pointsOfA = asOwnerOfA(this::loadJanuary).charts().dailyMinutes();
        List<ChartPointDto> pointsOfB = asOwnerOfB(this::loadJanuary).charts().dailyMinutes();

        assertThat(pointsOfA).anyMatch(point -> point.netMinutes() == 480);
        assertThat(pointsOfB)
                .as("mesmo intervalo, mesmo tipo de gráfico, tenant diferente")
                .allMatch(point -> point.netMinutes() == 0);
        assertThat(chartCache.size()).isGreaterThanOrEqualTo(2);
    }

    // ── Estados e casos extremos ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FA-01 / CA-17 / CX-01: tenant sem contratos devolve estrutura vazia, sem falhar")
    void tenantWithoutContracts() {
        DashboardResponse dashboard = asOwnerOfA(this::loadJanuary);

        assertThat(dashboard.contracts()).isEmpty();
        assertThat(dashboard.alerts()).isEmpty();
        assertThat(dashboard.quickStats().periodMinutes()).isZero();
        assertThat(dashboard.failedBlocks()).as("estrutura vazia não é falha de bloco").isEmpty();
    }

    @Test
    @DisplayName("CP-04 / CA-05 / INV-DSH-03: dailyMinutes traz 30 pontos, com zeros visíveis")
    void dailySeriesAlwaysHasThirtyPoints() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 17, 0)));

        List<ChartPointDto> points = asOwnerOfA(this::loadJanuary).charts().dailyMinutes();

        assertThat(points).hasSize(30);
        assertThat(points).filteredOn(point -> point.netMinutes() == 0).isNotEmpty();
        assertThat(points.get(29).date()).isEqualTo(JANUARY_END);
    }

    @Test
    @DisplayName("CP-06 / CA-12: os percentuais do gráfico por cliente somam 100")
    void clientChartPercentagesSumToOneHundred() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 17, 0)));

        var slices = asOwnerOfA(this::loadJanuary).charts().byClient();

        assertThat(slices).isNotEmpty();
        assertThat(
                        slices.stream()
                                .map(slice -> slice.percentage())
                                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo(new java.math.BigDecimal("100.00"));
    }

    @Test
    @DisplayName("RN-705 / CA-13: intervalo de 367 dias é rejeitado antes de qualquer agregação")
    void rejectsRangeAboveThreeHundredSixtySixDays() {
        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                dashboardService.load(
                                                        DashboardPeriodType.CUSTOM,
                                                        JANUARY_START,
                                                        JANUARY_START.plusDays(366))))
                .isInstanceOf(DashboardExceptions.DateRangeTooLargeException.class);
    }

    @Test
    @DisplayName("RS-03 / CP-05: recentWorkLogs traz no máximo 5 registros")
    void recentWorkLogsIsCappedAtFive() {
        var setup = asOwnerOfA(scenario::create);
        for (int day = 12; day <= 18; day++) {
            int workDay = day;
            asOwnerOfA(() -> workLogService.create(requestOn(setup, workDay, 9, 0, 10, 0)));
        }

        assertThat(asOwnerOfA(this::loadJanuary).recentWorkLogs()).hasSize(5);
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    /** Membro ativo do tenant A, sem nenhum vínculo com contratos (permissions.md §9, nota ²). */
    private UUID memberOfTenantA() {
        UUID memberId =
                inTransaction(
                        () ->
                                userRepository
                                        .save(
                                                FoundationDataBuilder.user(
                                                        "membro-" + UUID.randomUUID() + "@x.com",
                                                        NOW))
                                        .getId());
        runAs(
                tenantAId,
                userAId,
                Role.OWNER,
                () ->
                        membershipRepository.save(
                                FoundationDataBuilder.membership(
                                        tenantAId, memberId, Role.MEMBER, NOW)));
        return memberId;
    }

    private WorkLogCreateRequest request(
            WorkLogScenario.Scenario setup,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute) {
        return build(
                setup,
                WorkLogScenario.at(startHour, startMinute),
                WorkLogScenario.at(endHour, endMinute));
    }

    private WorkLogCreateRequest requestOn(
            WorkLogScenario.Scenario setup,
            int dayOfJanuary,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute) {
        LocalDate day = LocalDate.of(2026, 1, dayOfJanuary);
        return build(
                setup,
                WorkLogScenario.at(day, startHour, startMinute, 0),
                WorkLogScenario.at(day, endHour, endMinute, 0));
    }

    /**
     * Registra horas até atingir a fração pedida do saldo <b>do período</b>.
     *
     * <p>O contrato de teste contrata 2.400 minutos por mês, mas o primeiro período vai de 10/01 a
     * 31/01 e RN-217 o torna <b>proporcional</b> — cerca de 1.700 minutos, não 2.400. Os testes que
     * calculavam a porcentagem sobre 2.400 pediam 80% e produziam excedente, o que fazia a
     * severidade sair {@code CRITICAL} onde esperavam {@code WARNING}. Derivar do próprio período
     * mantém a asserção verdadeira mesmo que o calendário do cenário mude.
     *
     * <p>O total é dividido em dias distintos porque RN-102 rejeita sobreposição e nenhum registro
     * único cobriria a fração pedida dentro de um dia. O dia avança <b>entre chamadas</b>: RN-102
     * olha a pessoa e o horário, não o contrato, então dois contratos consumidos no mesmo dia pela
     * mesma pessoa colidiriam.
     */
    private void consume(WorkLogScenario.Scenario setup, double fracaoDoPeriodo) {
        int restante = (int) Math.round(setup.period().contractedMinutes() * fracaoDoPeriodo);
        while (restante > 0) {
            final int minutos = Math.min(restante, 8 * 60);
            final int dia = proximoDiaLivre++;
            asOwnerOfA(
                    () ->
                            workLogService.create(
                                    requestOn(setup, dia, 8, 0, 8 + minutos / 60, minutos % 60)));
            restante -= minutos;
        }
    }

    private WorkLogCreateRequest build(
            WorkLogScenario.Scenario setup, Instant startedAt, Instant endedAt) {
        return new WorkLogCreateRequest(
                setup.ticket().id(),
                startedAt,
                endedAt,
                0,
                "Trabalho registrado para o painel",
                setup.category().id(),
                true,
                List.of(),
                null);
    }
}
