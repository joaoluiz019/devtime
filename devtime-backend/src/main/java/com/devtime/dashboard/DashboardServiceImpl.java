package com.devtime.dashboard;

import com.devtime.client.ClientService;
import com.devtime.client.dto.ClientResponses.ClientRef;
import com.devtime.contract.BalanceService;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.ContractResponses.ContractDashboardCard;
import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.dashboard.domain.DashboardScope;
import com.devtime.dashboard.dto.DashboardResponses.ContractStatusDto;
import com.devtime.dashboard.dto.DashboardResponses.DashboardAlertDto;
import com.devtime.dashboard.dto.DashboardResponses.DashboardChartsDto;
import com.devtime.dashboard.dto.DashboardResponses.DashboardPeriodDto;
import com.devtime.dashboard.dto.DashboardResponses.DashboardResponse;
import com.devtime.dashboard.dto.DashboardResponses.QuickStatsDto;
import com.devtime.shared.time.DateRange;
import com.devtime.shared.time.TenantClock;
import com.devtime.ticket.TicketService;
import com.devtime.ticket.dto.TicketResponses.TicketDashboardItem;
import com.devtime.timer.TimerQueryService;
import com.devtime.worklog.WorkLogAggregationService;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogFilter;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogRangeTotals;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogSummaryResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Orquestração dos blocos do painel (ver {@link DashboardService}).
 *
 * <p><b>Sem {@code @Transactional}, e isso é deliberado</b> (BR-121 descreve o caso comum, não
 * este). Cada colaborador abre a própria transação de leitura, o que é o que torna o "erro parcial"
 * de §10 possível: dentro de uma única transação, a primeira consulta que falhasse marcaria a
 * unidade de trabalho como {@code rollback-only} e derrubaria todos os blocos seguintes —
 * transformando a falha de um gráfico em tela branca, exatamente o que CP-08 proíbe.
 *
 * <p><b>Execução sequencial, não paralela.</b> O item correspondente do checklist de §34 não é
 * cumprido, e a razão é de segurança: {@link com.devtime.shared.tenancy.TenantContext} é um {@code
 * ThreadLocal} e o filtro de tenant do Hibernate é ligado por transação, ambos presos à thread da
 * requisição (backend.md §7.2). Distribuir os blocos por um pool exigiria propagar a sessão
 * manualmente a cada tarefa, e um único ponto esquecido produziria consulta sem filtro de tenant —
 * a falha mais grave do modelo de ameaças (ART-021, SG-01). Os índices cobertos de V031 são o que
 * sustenta RNF-003; a paralelização é otimização a ser medida antes de aplicada (CG-10). A
 * divergência está registrada no CHANGELOG.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    /** RS-03 / CP-05: é atalho, não listagem. */
    private static final int RECENT_WORK_LOGS = 5;

    /** RN-012: toda listagem interna tem teto. CX-07: os mais críticos primeiro. */
    private static final int OPEN_TICKETS_LIMIT = 20;

    private static final String BLOCK_QUICK_STATS = "quickStats";
    private static final String BLOCK_CONTRACTS = "contracts";
    private static final String BLOCK_ALERTS = "alerts";
    private static final String BLOCK_RECENT_WORK_LOGS = "recentWorkLogs";
    private static final String BLOCK_OPEN_TICKETS = "openTickets";
    private static final String BLOCK_CHARTS = "charts";

    private final DashboardPeriodResolver periodResolver;
    private final DashboardScopeResolver scopeResolver;
    private final DashboardMapper mapper;
    private final DashboardAlertService alertService;
    private final DashboardChartService chartService;
    private final DashboardMetrics metrics;
    private final WorkLogAggregationService aggregationService;
    private final WorkLogService workLogService;
    private final ContractService contractService;
    private final BalanceService balanceService;
    private final ClientService clientService;
    private final TicketService ticketService;
    private final TimerQueryService timerQueryService;
    private final TenantClock clock;

    @Override
    @PreAuthorize(
            "hasPermission(null, 'DASHBOARD_VIEW_ANY') or hasPermission(null,"
                    + " 'DASHBOARD_VIEW_OWN')")
    public DashboardResponse load(DashboardPeriodType period, LocalDate from, LocalDate to) {
        long startedAt = System.nanoTime();
        DateRange range = resolveRange(period, from, to);
        DashboardScope scope = scopeResolver.resolve();
        LocalDate today = clock.today();

        List<String> failedBlocks = new ArrayList<>();

        QuickStatsDto quickStats =
                block(
                        BLOCK_QUICK_STATS,
                        failedBlocks,
                        () -> quickStats(range, today),
                        emptyStats());

        ContractsBlock contractsBlock =
                block(
                        BLOCK_CONTRACTS,
                        failedBlocks,
                        () -> contracts(scope, today),
                        ContractsBlock.empty());
        List<ContractStatusDto> contracts = contractsBlock.statuses();

        List<DashboardAlertDto> alerts =
                contracts.isEmpty()
                        ? List.of()
                        : block(
                                BLOCK_ALERTS,
                                failedBlocks,
                                () ->
                                        alertService.deriveFrom(
                                                contracts, contractsBlock.cardsById()),
                                List.of());

        List<WorkLogSummaryResponse> recentWorkLogs =
                block(BLOCK_RECENT_WORK_LOGS, failedBlocks, this::recentWorkLogs, List.of());

        List<TicketDashboardItem> openTickets =
                block(
                        BLOCK_OPEN_TICKETS,
                        failedBlocks,
                        () -> ticketService.findOpenForCurrentUser(OPEN_TICKETS_LIMIT),
                        List.of());

        DashboardChartsDto charts =
                block(
                        BLOCK_CHARTS,
                        failedBlocks,
                        () -> chartService.mainCharts(range),
                        new DashboardChartsDto(List.of(), List.of(), List.of()));

        metrics.recordLoad(scope, System.nanoTime() - startedAt);
        metrics.recordContracts(contracts);
        // §28: carga em DEBUG. O painel é a tela mais acessada do produto, e registrar cada
        // abertura
        // em INFO inundaria o log sem valor investigativo.
        log.debug(
                "painel carregado scope={} period={} de {} a {} contratos={} blocosComFalha={}",
                scope,
                period,
                range.start(),
                range.end(),
                contracts.size(),
                failedBlocks);

        return new DashboardResponse(
                new DashboardPeriodDto(
                        period == null ? DashboardPeriodType.CURRENT_PERIOD : period,
                        range.start(),
                        range.end()),
                scope,
                quickStats,
                contracts,
                alerts,
                recentWorkLogs,
                openTickets,
                charts,
                List.copyOf(failedBlocks));
    }

    /**
     * Resolve o intervalo, registrando a rejeição por tamanho.
     *
     * <p>§28: intervalo rejeitado é {@code INFO} — não é falha do sistema, e o crescimento da
     * métrica correspondente indica um seletor de tela permitindo o que a API recusa.
     */
    private DateRange resolveRange(DashboardPeriodType period, LocalDate from, LocalDate to) {
        try {
            return periodResolver.resolve(period, from, to);
        } catch (
                com.devtime.dashboard.domain.DashboardExceptions.DateRangeTooLargeException
                        exception) {
            log.info("intervalo do painel rejeitado de={} para={}", from, to);
            metrics.recordRejectedRange();
            throw exception;
        }
    }

    /**
     * Executa um bloco isolando a sua falha (§10, CP-08, OB-05).
     *
     * <p>ER-08: o painel é a soma de seis leituras independentes, e nenhuma delas é essencial às
     * outras. Falhar tudo por causa de um gráfico faria a falha menos importante apagar os cartões
     * de contrato, que são o motivo de a tela existir.
     */
    private <T> T block(String name, List<String> failedBlocks, Supplier<T> supplier, T fallback) {
        long startedAt = System.nanoTime();
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            // §28: falha de bloco é WARN — indica degradação parcial visível ao usuário. O corpo do
            // bloco não é registrado: a mensagem descreve a falha, nunca os dados.
            log.warn("bloco do painel falhou bloco={} causa={}", name, exception.toString());
            metrics.recordBlockFailure(name);
            failedBlocks.add(name);
            return fallback;
        } finally {
            metrics.recordBlock(name, System.nanoTime() - startedAt);
        }
    }

    private QuickStatsDto quickStats(DateRange range, LocalDate today) {
        WorkLogRangeTotals todayTotals = aggregationService.totalsInRange(today, today);
        // Semana começa na segunda-feira, como o calendário de P22 já a apresenta.
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        WorkLogRangeTotals weekTotals = aggregationService.totalsInRange(weekStart, today);
        WorkLogRangeTotals periodTotals =
                aggregationService.totalsInRange(range.start(), range.end());
        return mapper.toQuickStats(
                todayTotals,
                weekTotals,
                periodTotals,
                timerQueryService.activeMinutesInCurrentTenant());
    }

    /**
     * Cartões e as suas origens, devolvidos juntos.
     *
     * <p>Os alertas precisam dos limiares e da vigência, que só o cartão de origem carrega.
     * Buscá-los de novo executaria a mesma consulta duas vezes e — pior — permitiria que o alerta
     * descrevesse um estado diferente do que o cartão ao lado dele exibe.
     */
    private record ContractsBlock(
            List<ContractStatusDto> statuses, Map<UUID, ContractDashboardCard> cardsById) {

        static ContractsBlock empty() {
            return new ContractsBlock(List.of(), Map.of());
        }
    }

    /** CP-02: ordenado por severidade e depois por dias restantes. */
    private ContractsBlock contracts(DashboardScope scope, LocalDate today) {
        List<ContractDashboardCard> cards =
                contractService.findActiveForDashboard(scope == DashboardScope.USER);
        if (cards.isEmpty()) {
            // FA-01 / CX-01: tenant sem contratos, nenhuma agregação de saldo executada.
            return ContractsBlock.empty();
        }

        Map<UUID, ClientRef> clients =
                clientService
                        .findRefs(
                                cards.stream()
                                        .map(ContractDashboardCard::clientId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(ClientRef::id, Function.identity()));

        List<ContractStatusDto> statuses =
                cards.stream()
                        .filter(card -> card.periodId() != null)
                        .map(
                                card -> {
                                    ClientRef client = clients.get(card.clientId());
                                    // INV-DSH-01: o saldo vem de 011, sempre. Nenhuma fórmula aqui.
                                    return mapper.toContractStatus(
                                            card,
                                            balanceService.getBalance(card.periodId()),
                                            client == null ? null : client.name(),
                                            client == null ? null : client.color(),
                                            today);
                                })
                        .toList();

        Map<UUID, ContractDashboardCard> cardsById =
                cards.stream()
                        .collect(
                                Collectors.toMap(
                                        ContractDashboardCard::contractId,
                                        Function.identity(),
                                        (first, second) -> first,
                                        LinkedHashMap::new));
        return new ContractsBlock(mapper.sortByCriticality(statuses), cardsById);
    }

    /**
     * RS-03: os cinco registros mais recentes.
     *
     * <p>Passa por {@code WorkLogService.search}, que já aplica o escopo de dados de {@code MEMBER}
     * (IMP-02) — consultar por fora reimplementaria a restrição em um segundo lugar.
     */
    private List<WorkLogSummaryResponse> recentWorkLogs() {
        WorkLogFilter filter =
                new WorkLogFilter(null, null, null, null, null, null, null, null, null, null, null);
        return workLogService
                .search(
                        filter,
                        PageRequest.of(
                                0, RECENT_WORK_LOGS, Sort.by(Sort.Direction.DESC, "startedAt")))
                .content();
    }

    private QuickStatsDto emptyStats() {
        return mapper.toQuickStats(
                WorkLogRangeTotals.empty(),
                WorkLogRangeTotals.empty(),
                WorkLogRangeTotals.empty(),
                0);
    }
}
