package com.devtime.worklog;

import com.devtime.audit.AuditService;
import com.devtime.category.CategoryService;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import com.devtime.contract.BalanceService;
import com.devtime.contract.ContractPeriodService;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.ContractResponses.ContractPeriodRefResponse;
import com.devtime.contract.dto.ContractResponses.ContractWorkLogRefResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.pagination.PageRequestFactory;
import com.devtime.shared.pagination.PageResponse;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.tag.TagLinkService;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.tenant.TenantSettingsService;
import com.devtime.tenant.dto.TenantSettings;
import com.devtime.ticket.TicketService;
import com.devtime.ticket.TicketTotalsService;
import com.devtime.ticket.TicketTransitionService;
import com.devtime.ticket.dto.TicketResponses.TicketWorkLogRefResponse;
import com.devtime.user.UserService;
import com.devtime.worklog.domain.WorkLog;
import com.devtime.worklog.domain.WorkLogExceptions;
import com.devtime.worklog.domain.WorkLogInterval;
import com.devtime.worklog.domain.WorkLogSource;
import com.devtime.worklog.dto.WorkLogFilter;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogDuplicateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogUpdateRequest;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCategoryResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCreatedResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogSummaryResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogTicketResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogWarning;
import com.devtime.worklog.event.WorkLogEvents.WorkLogCreatedEvent;
import com.devtime.worklog.event.WorkLogEvents.WorkLogDeletedEvent;
import com.devtime.worklog.event.WorkLogEvents.WorkLogUpdatedEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida do registro de horas (spec 008 §6).
 *
 * <p><b>A ordem de {@link #create} é normativa</b> (SV-03, BR-062) e reproduz literalmente a §6.1
 * da spec. Ela não é uma sugestão de organização: determina <b>qual erro o usuário vê</b> quando o
 * payload viola várias regras ao mesmo tempo (OB-04). Três decisões da ordem merecem registro:
 *
 * <ul>
 *   <li>A <b>sobreposição precede o cálculo</b>: a detecção usa apenas {@code startedAt} e {@code
 *       endedAt}, já disponíveis, e "já existe um registro neste intervalo" é mais acionável que
 *       "tempo líquido inválido" quando as duas se aplicam.
 *   <li>A <b>resolução do período vem depois das validações puras</b>: é a única verificação que
 *       exige consulta a outra feature, e uma requisição obviamente inválida não deve gerar I/O.
 *   <li>A <b>política de excedente é a última</b>: depende do {@code billableMinutes} calculado e
 *       do período resolvido, e é a única cujo resultado pode ser um aviso em vez de um erro.
 * </ul>
 *
 * <p>§28 / CP-18: <b>{@code description} nunca entra em log</b>. É texto livre e pode conter dado
 * pessoal de terceiros (§19.1); os identificadores bastam para qualquer investigação.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class WorkLogServiceImpl implements WorkLogService {

    private static final String ENTITY_TYPE = "WorkLog";

    private final WorkLogRepository repository;
    private final WorkLogMapper mapper;
    private final WorkLogCalculator calculator;
    private final RoundingPolicy roundingPolicy;
    private final WorkDateResolver workDateResolver;
    private final OverlapDetector overlapDetector;
    private final WorkLogValidator validator;
    private final ContractValidityValidator contractValidityValidator;
    private final RetroactiveWindowPolicy retroactiveWindowPolicy;
    private final LockedPeriodGuard lockedPeriodGuard;
    private final PeriodTransferGuard periodTransferGuard;
    private final WorkLogOwnershipPolicy ownershipPolicy;
    private final OveragePolicyEvaluator overagePolicyEvaluator;
    private final TicketService ticketService;
    private final TicketTotalsService ticketTotalsService;
    private final TicketTransitionService ticketTransitionService;
    private final ContractService contractService;
    private final ContractPeriodService contractPeriodService;
    private final BalanceService balanceService;
    private final CategoryService categoryService;
    private final TagLinkService tagLinkService;
    private final UserService userService;
    private final TenantSettingsService tenantSettingsService;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final PageRequestFactory pageRequestFactory;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    // ── Leitura ──────────────────────────────────────────────────────────────────────────────

    @Override
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_VIEW_ANY') or hasPermission(null, 'WORKLOG_VIEW_OWN')")
    public PageResponse<WorkLogSummaryResponse> search(WorkLogFilter filter, Pageable pageable) {
        Pageable validated = pageRequestFactory.validate(pageable); // RN-012

        List<UUID> idsWithTags =
                filter.tagIds() == null || filter.tagIds().isEmpty()
                        ? null
                        : tagLinkService.workLogIdsWithAllTags(filter.tagIds());

        // IMP-02 / SG-03: o escopo de MEMBER entra na consulta, não em memória — o filtro precisa
        // valer também para o count da paginação, ou a contagem revelaria registros invisíveis.
        Page<WorkLog> page =
                repository.findAll(
                        WorkLogSpecifications.withFilters(
                                filter,
                                ownershipPolicy.dataScopeUserId().orElse(null),
                                idsWithTags),
                        validated);

        Map<UUID, String> ticketKeys = ticketKeysOf(page.getContent());
        Map<UUID, String> categoryNames = categoryNames();
        return PageResponse.of(
                page,
                workLog ->
                        mapper.toSummary(
                                workLog,
                                ticketKeys.get(workLog.getTicketId()),
                                categoryNames.get(workLog.getCategoryId())));
    }

    @Override
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_VIEW_ANY') or hasPermission(null, 'WORKLOG_VIEW_OWN')")
    public WorkLogResponse getById(UUID id) {
        return toResponse(requireVisible(id));
    }

    // ── Criação ──────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'WORKLOG_CREATE')")
    public WorkLogCreatedResponse create(WorkLogCreateRequest request) {
        return persist(
                new WorkLogCommand(
                        request.ticketId(),
                        request.startedAt(),
                        request.endedAt(),
                        request.pausedMinutes() == null ? 0 : request.pausedMinutes(),
                        request.description(),
                        request.categoryId(),
                        request.billable(),
                        request.tagIds(),
                        request.userId(),
                        WorkLogSource.MANUAL,
                        null));
    }

    /**
     * RN-159: o cronômetro <b>delega ao mesmo caminho</b>, sem duplicar validação (CP-14).
     *
     * <p>Este método monta o comando e chama {@link #persist}; não existe uma linha de regra aqui
     * que não exista na criação manual. Duplicar as validações criaria dois conjuntos que
     * divergiriam na primeira alteração — o modo de falha que RN-159 existe para impedir.
     *
     * <p>RN-160: qualquer exceção propaga intacta, e é o chamador em {@code 009} que preserva o
     * timer. Nada aqui conhece o cronômetro além do identificador que carimba {@code timerId}.
     */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public WorkLogCreatedResponse createFromTimer(
            UUID timerId,
            UUID ticketId,
            UUID categoryId,
            UUID userId,
            Instant startedAt,
            Instant endedAt,
            int pausedMinutes,
            String description,
            boolean billable) {
        return persist(
                new WorkLogCommand(
                        ticketId,
                        startedAt,
                        endedAt,
                        pausedMinutes,
                        description,
                        categoryId,
                        billable,
                        List.of(),
                        userId,
                        WorkLogSource.TIMER,
                        timerId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'WORKLOG_CREATE')")
    public WorkLogCreatedResponse duplicate(UUID id, WorkLogDuplicateRequest request) {
        WorkLog original = requireVisible(id);
        // FA-14 / CX-28: o horário é obrigatoriamente novo. Duplicar mantendo o intervalo criaria
        // uma sobreposição perfeita, rejeitada em seguida por RN-102 — a validação continua sendo
        // a mesma, e é ela que garante a regra.
        return persist(
                new WorkLogCommand(
                        original.getTicketId(),
                        request.startedAt(),
                        request.endedAt(),
                        original.getPausedMinutes(),
                        original.getDescription(),
                        original.getCategoryId(),
                        original.isBillable(),
                        tagLinkService.findByWorkLogId(id).stream()
                                .map(TagOptionResponse::id)
                                .toList(),
                        original.getUserId(),
                        WorkLogSource.MANUAL,
                        null));
    }

    // ── Edição e exclusão ────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_UPDATE_ANY') or hasPermission(null, 'WORKLOG_UPDATE_OWN')")
    public WorkLogCreatedResponse update(UUID id, WorkLogUpdateRequest request) {
        WorkLog workLog = requireVisible(id);
        assertVersion(workLog, request.version()); // RN-004
        ownershipPolicy.assertCanUpdate(workLog); // RN-122
        lockedPeriodGuard.assertNotLocked(workLog); // RN-121, OWN-02

        Map<String, Object> before = describe(workLog);
        UUID previousPeriodId = workLog.getContractPeriodId();
        int previousBillable = workLog.billableMinutes();
        int previousNonBillable = workLog.isBillable() ? 0 : workLog.getNetMinutes();
        int previousNet = workLog.getNetMinutes();
        UUID previousTicketId = workLog.getTicketId();

        // §6.1 revalidada integralmente: uma edição pode introduzir qualquer violação que a
        // criação impediria, e verificar só o que mudou exigiria saber, campo a campo, quais
        // regras cada um alcança.
        ValidatedWorkLog validated =
                validate(
                        new WorkLogCommand(
                                request.ticketId(),
                                request.startedAt(),
                                request.endedAt(),
                                request.pausedMinutes() == null ? 0 : request.pausedMinutes(),
                                request.description(),
                                request.categoryId(),
                                request.billable(),
                                request.tagIds(),
                                workLog.getUserId(), // 🔒 RN-106: o dono não muda na edição
                                workLog.getSource(), // 🔒 RN-126
                                workLog.getTimerId()),
                        id);

        // RN-124: mover entre períodos exige ambos abertos. O destino já foi verificado como
        // gravável em validate(); aqui entra a origem.
        if (!validated.period().id().equals(previousPeriodId)) {
            periodTransferGuard.assertTransferable(
                    contractPeriodService.getRefById(previousPeriodId), validated.period());
        }

        apply(workLog, validated);
        workLog.setEditCount(workLog.getEditCount() + 1); // RN-123

        tagLinkService.replaceWorkLogTags(
                id, request.tagIds() == null ? List.of() : request.tagIds());

        // RN-123: os desnormalizados são recalculados por delta, nunca por reagregação (CP-15).
        applyTicketDelta(previousTicketId, -previousNet, -previousBillable);
        applyTicketDelta(workLog.getTicketId(), workLog.getNetMinutes(), workLog.billableMinutes());
        balanceService.applyConsumptionDelta(
                previousPeriodId, -previousBillable, -previousNonBillable);
        balanceService.applyConsumptionDelta(
                workLog.getContractPeriodId(),
                workLog.billableMinutes(),
                workLog.isBillable() ? 0 : workLog.getNetMinutes());

        auditService.record(ENTITY_TYPE + "_UPDATED", ENTITY_TYPE, id, before, describe(workLog));
        if (!validated.period().id().equals(previousPeriodId)) {
            auditService.record(
                    "WORK_LOG_PERIOD_CHANGED",
                    ENTITY_TYPE,
                    id,
                    Map.of("contractPeriodId", previousPeriodId),
                    Map.of("contractPeriodId", validated.period().id()));
        }
        events.publish(
                new WorkLogUpdatedEvent(
                        id,
                        workLog.getContractPeriodId(),
                        workLog.getTicketId(),
                        workLog.billableMinutes() - previousBillable));

        log.info(
                "work log editado workLogId={} editCount={} netMinutes={}",
                id,
                workLog.getEditCount(),
                workLog.getNetMinutes());
        return new WorkLogCreatedResponse(
                toResponse(workLog),
                balanceService.getBalance(workLog.getContractPeriodId()),
                validated.warnings());
    }

    @Override
    @Transactional
    @PreAuthorize(
            "hasPermission(null, 'WORKLOG_DELETE_ANY') or hasPermission(null, 'WORKLOG_DELETE_OWN')")
    public void delete(UUID id) {
        WorkLog workLog = requireVisible(id);
        ownershipPolicy.assertCanDelete(workLog); // RN-122
        lockedPeriodGuard.assertNotLocked(workLog); // RN-121

        int netMinutes = workLog.getNetMinutes();
        int billableMinutes = workLog.billableMinutes();
        int nonBillableMinutes = workLog.isBillable() ? 0 : netMinutes;

        tagLinkService.unlinkAllFromWorkLog(id); // INV-TAG-04
        repository.softDelete(
                id, clock.now(), tenantContext.currentUserId().orElse(null)); // RN-003

        // RN-125: devolve o saldo ao período e reduz o total do ticket, na mesma transação.
        applyTicketDelta(workLog.getTicketId(), -netMinutes, -billableMinutes);
        balanceService.applyConsumptionDelta(
                workLog.getContractPeriodId(), -billableMinutes, -nonBillableMinutes);

        auditService.record(
                "WORK_LOG_DELETED",
                ENTITY_TYPE,
                id,
                Map.of(
                        "netMinutes", netMinutes,
                        "billableMinutes", billableMinutes,
                        "contractPeriodId", workLog.getContractPeriodId()),
                Map.of("deletedAt", clock.now().toString()));
        // CE-11 / CX-20: a notificação de limiar anterior NÃO é removida. O dedupeKey impede um
        // alerta novo se o consumo subir de novo, e apagar o histórico esconderia que o limiar
        // chegou a ser cruzado.
        events.publish(
                new WorkLogDeletedEvent(
                        id,
                        workLog.getContractPeriodId(),
                        workLog.getTicketId(),
                        -billableMinutes));
        log.info("work log excluído workLogId={} netMinutes={}", id, netMinutes);
    }

    // ── Interfaces públicas consumidas por outras features ───────────────────────────────────

    @Override
    public long countByTicket(UUID ticketId) {
        return repository.countByTicketId(ticketId);
    }

    @Override
    public long countByCategory(UUID categoryId) {
        return repository.countByCategoryId(categoryId);
    }

    @Override
    public int sumBillableMinutesByPeriod(UUID periodId) {
        return repository.sumBillableMinutesByPeriod(periodId);
    }

    @Override
    public int sumNonBillableMinutesByPeriod(UUID periodId) {
        return repository.sumNonBillableMinutesByPeriod(periodId);
    }

    /**
     * Sem {@code @PreAuthorize}: consumido por {@code 011} dentro do fechamento e do extrato, que
     * já verificaram {@code PERIOD_CLOSE}/{@code PERIOD_VIEW}. O escopo de dados de {@code MEMBER}
     * <b>não</b> se aplica: o extrato do período é a visão financeira do contrato, não a lista
     * pessoal de registros, e omitir linhas dele produziria um saldo que não fecha.
     */
    @Override
    public List<com.devtime.contract.dto.BalanceResponses.PeriodWorkLogEntry>
            findByPeriodForStatement(UUID periodId) {
        List<WorkLog> workLogs = repository.findByPeriod(periodId);
        Map<UUID, String> ticketKeys = ticketKeysOf(workLogs);
        Map<UUID, String> categoryNames = categoryNames();
        return workLogs.stream()
                .map(
                        workLog ->
                                new com.devtime.contract.dto.BalanceResponses.PeriodWorkLogEntry(
                                        workLog.getId(),
                                        workLog.getWorkDate(),
                                        ticketKeys.get(workLog.getTicketId()),
                                        categoryNames.get(workLog.getCategoryId()),
                                        workLog.getUserId(),
                                        workLog.getDescription(),
                                        workLog.getNetMinutes(),
                                        workLog.billableMinutes(),
                                        workLog.isBillable()))
                .toList();
    }

    @Override
    @Transactional
    public int lockByPeriod(UUID periodId) {
        int locked = repository.lockByPeriod(periodId, clock.now()); // RN-241 passo 3
        if (locked > 0) {
            auditService.recordSystemAction(
                    "WORK_LOG_LOCKED",
                    ENTITY_TYPE,
                    periodId,
                    Map.of("lockedWorkLogs", locked, "lockedAt", clock.now().toString()));
        }
        return locked;
    }

    @Override
    @Transactional
    public int unlockByPeriod(UUID periodId) {
        int unlocked = repository.unlockByPeriod(periodId, clock.now()); // RN-243
        if (unlocked > 0) {
            auditService.recordSystemAction(
                    "WORK_LOG_UNLOCKED",
                    ENTITY_TYPE,
                    periodId,
                    Map.of("unlockedWorkLogs", unlocked));
        }
        return unlocked;
    }

    // ── Ordem normativa da §6.1 ──────────────────────────────────────────────────────────────

    /**
     * Comando interno unificado de criação.
     *
     * <p>Existe para que {@link #create}, {@link #createFromTimer} e {@link #duplicate} convirjam
     * em um único caminho: a origem do dado muda, a regra não (RN-159).
     */
    private record WorkLogCommand(
            UUID ticketId,
            Instant startedAt,
            Instant endedAt,
            int pausedMinutes,
            String description,
            UUID categoryId,
            Boolean billable,
            List<UUID> tagIds,
            UUID userId,
            WorkLogSource source,
            UUID timerId) {}

    /** Resultado das validações puras e das consultas — tudo o que a persistência precisa. */
    private record ValidatedWorkLog(
            TicketWorkLogRefResponse ticket,
            ContractWorkLogRefResponse contract,
            ContractPeriodRefResponse period,
            CategoryResponse category,
            UUID ownerId,
            WorkLogInterval interval,
            LocalDate workDate,
            int grossMinutes,
            int pausedMinutes,
            int netMinutes,
            boolean billable,
            String description,
            List<WorkLogWarning> warnings) {}

    private WorkLogCreatedResponse persist(WorkLogCommand command) {
        ValidatedWorkLog validated = validate(command, null);

        // Passo 21 — persistir, copiando contractId e clientId do ticket (RN-109).
        WorkLog workLog = new WorkLog();
        workLog.setContractId(validated.contract().id());
        workLog.setClientId(validated.contract().clientId());
        workLog.setUserId(validated.ownerId());
        workLog.setSource(command.source());
        workLog.setTimerId(command.timerId());
        workLog.setEditCount(0);
        apply(workLog, validated);
        WorkLog saved = repository.save(workLog);

        // Passo 22 — vincular etiquetas (INV-TAG-01).
        tagLinkService.replaceWorkLogTags(
                saved.getId(), command.tagIds() == null ? List.of() : command.tagIds());

        // Passo 23 — totais do ticket (RN-308) e reabertura se estava DONE (RN-312).
        applyTicketDelta(saved.getTicketId(), saved.getNetMinutes(), saved.billableMinutes());
        ticketTransitionService.reopenOnWorkLog(saved.getTicketId(), saved.getId());

        // Passo 24 — consumo do período, dentro da transação (OB-06).
        balanceService.applyConsumptionDelta(
                saved.getContractPeriodId(),
                saved.billableMinutes(),
                saved.isBillable() ? 0 : saved.getNetMinutes());

        auditService.record(
                "WORK_LOG_CREATED",
                ENTITY_TYPE,
                saved.getId(),
                Map.of(),
                Map.of(
                        "ticketId", saved.getTicketId(),
                        "workDate", saved.getWorkDate().toString(),
                        "netMinutes", saved.getNetMinutes(),
                        "billable", saved.isBillable(),
                        "source", saved.getSource().name()),
                // RN-106: quem lançou em nome de quem é a informação que sustenta o registro.
                Map.of("createdFor", saved.getUserId()));

        // Passo 25 — publicado após a persistência (BR-182); 013 avalia os limiares (RN-602).
        events.publish(
                new WorkLogCreatedEvent(
                        saved.getId(),
                        saved.getContractPeriodId(),
                        saved.getTicketId(),
                        saved.billableMinutes()));

        // §28: nem descrição, nem título do ticket.
        log.info(
                "work log criado workLogId={} ticketKey={} netMinutes={} source={}",
                saved.getId(),
                validated.ticket().key(),
                saved.getNetMinutes(),
                saved.getSource());

        // Passo 26 — o saldo já atualizado acompanha a resposta.
        return new WorkLogCreatedResponse(
                toResponse(saved),
                balanceService.getBalance(saved.getContractPeriodId()),
                validated.warnings());
    }

    /**
     * Passos 3 a 20 da §6.1, <b>nesta ordem exata</b>.
     *
     * <p>Compartilhado por criação e edição: a edição pode introduzir qualquer violação que a
     * criação impediria, e revalidar tudo é mais barato — em código e em risco — do que mapear
     * quais regras cada campo alcança.
     *
     * @param excludeId próprio identificador na edição, excluído da comparação de RN-102 (CX-17)
     */
    private ValidatedWorkLog validate(WorkLogCommand command, UUID excludeId) {
        TenantSettings settings = tenantSettingsService.current();

        // Passo 3 — RN-106: dono do registro; terceiro exige permissão e membro ativo.
        UUID ownerId = ownershipPolicy.resolveOwner(command.userId());

        // Passo 4 — RN-101: o ticket existe no tenant. Passo 5 — RN-306: o contrato aceita
        // registro. Ambos resolvidos por quem é dono das regras (AR-02).
        if (command.ticketId() == null) {
            throw WorkLogExceptions.ticketRequired();
        }
        TicketWorkLogRefResponse ticket = ticketService.getRefForWorkLog(command.ticketId());
        ContractWorkLogRefResponse contract = contractService.getWorkLogRef(ticket.contractId());

        WorkLogInterval interval = new WorkLogInterval(command.startedAt(), command.endedAt());

        validator.assertChronological(interval); // Passo 6 — RN-114
        int grossMinutes = calculator.grossMinutes(interval.startedAt(), interval.endedAt());
        validator.assertWithinMaxDuration(grossMinutes); // Passo 7 — RN-103
        contractValidityValidator.assertWithinValidity(
                interval.startedAt(), contract.startDate(), contract.endDate()); // Passo 8 — RN-117
        validator.assertNotInFuture(interval); // Passo 9 — RN-118

        // RN-108: a data local do início decide o dia e, por consequência, o período (CE-02).
        LocalDate workDate = workDateResolver.resolve(interval.startedAt());
        validator.assertFutureDateAllowed(workDate, settings.allowFutureWorkLogs()); // Passo 10
        retroactiveWindowPolicy.assertWithinWindow(
                workDate, settings.retroactiveLimitDays()); // Passo 11 — RN-120

        // Passo 12 — RN-102. Precede o cálculo de propósito: usa apenas o que já está em mãos, e
        // a sobreposição é o problema mais difícil de o usuário perceber sozinho.
        overlapDetector.assertNoOverlap(ownerId, interval, excludeId);

        // Passo 13 — RN-110 a RN-113.
        validator.assertPausedMinutesCoherent(command.pausedMinutes(), grossMinutes); // Passo 14
        int netBeforeRounding = calculator.netMinutes(grossMinutes, command.pausedMinutes());
        int netMinutes = roundingPolicy.roundDown(netBeforeRounding, settings.roundingMinutes());
        validator.assertPositiveNetMinutes(netMinutes, netBeforeRounding); // Passo 15 — RN-115

        // Passo 16 — RN-104: categoria informada precisa estar ativa; ausente aciona a cadeia.
        CategoryResponse category = resolveCategory(command.categoryId(), ticket, contract);
        boolean billable =
                command.billable() == null ? category.billableByDefault() : command.billable();

        String description = validator.requireDescription(command.description()); // Passo 17

        // Passo 18 — RN-107. Última verificação, porque é a que exige consulta a outra feature.
        ContractPeriodRefResponse period =
                contractPeriodService
                        .resolvePeriodRef(contract.id(), workDate)
                        .orElseThrow(() -> WorkLogExceptions.noPeriodForDate(workDate));
        // Passo 19 — o período precisa aceitar escrita (OPEN ou REOPENED).
        if (!period.acceptsWorkLogs()) {
            throw WorkLogExceptions.locked(period.id());
        }

        // Passo 20 — RN-231 a RN-234. Por último: depende do billableMinutes e do período.
        List<WorkLogWarning> warnings =
                overagePolicyEvaluator.evaluate(
                        period.id(), calculator.billableMinutes(netMinutes, billable));

        return new ValidatedWorkLog(
                ticket,
                contract,
                period,
                category,
                ownerId,
                interval,
                workDate,
                grossMinutes,
                command.pausedMinutes(),
                netMinutes,
                billable,
                description,
                warnings);
    }

    /** Campos derivados da validação; nunca vindos do payload (SG-06, SG-09). */
    private void apply(WorkLog workLog, ValidatedWorkLog validated) {
        workLog.setTicketId(validated.ticket().id());
        workLog.setContractPeriodId(validated.period().id());
        workLog.setCategoryId(validated.category().id());
        workLog.setWorkDate(validated.workDate());
        workLog.setStartedAt(validated.interval().startedAt());
        workLog.setEndedAt(validated.interval().endedAt());
        workLog.setGrossMinutes(validated.grossMinutes());
        workLog.setPausedMinutes(validated.pausedMinutes());
        workLog.setNetMinutes(validated.netMinutes());
        workLog.setDescription(validated.description());
        workLog.setBillable(validated.billable());
    }

    /**
     * RN-104: categoria informada, ou a cadeia de pré-seleção.
     *
     * <p><b>Limitação conhecida:</b> o terceiro elo da cadeia — a preferência do usuário — chega
     * como {@code null}. {@code user.preferences.defaultCategoryId} pertence a {@code 002-users},
     * que ainda não expõe preferências (ver Pendências no {@code CHANGELOG.md}). A cadeia degrada
     * para ticket → contrato → primeira ativa, que é a ordem correta com um elo a menos, e o ponto
     * de extensão está isolado neste único parâmetro.
     */
    private CategoryResponse resolveCategory(
            UUID requestedCategoryId,
            TicketWorkLogRefResponse ticket,
            ContractWorkLogRefResponse contract) {
        if (requestedCategoryId != null) {
            try {
                return categoryService.requireActive(requestedCategoryId);
            } catch (EntityNotFoundException inexistentOrInactive) {
                // CategoryService responde 404 para inexistente, de outro tenant ou inativa; o
                // contrato de worklogs.md §12 exige DEVTIME-2104 no campo categoryId.
                throw WorkLogExceptions.categoryInvalid();
            }
        }
        return categoryService
                .resolveForWorkLog(ticket.defaultCategoryId(), contract.defaultCategoryId(), null)
                .orElseThrow(WorkLogExceptions::categoryInvalid);
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private void applyTicketDelta(UUID ticketId, int netDelta, int billableDelta) {
        ticketTotalsService.applyWorkLogDelta(ticketId, netDelta, billableDelta); // RN-308
    }

    /**
     * CE-P-04: registro inexistente, de outro tenant <b>ou de colega para {@code MEMBER}</b>
     * responde igualmente {@code 404}.
     *
     * <p>Um {@code 403} no último caso confirmaria que o registro existe — e a existência já é
     * informação sobre quem trabalhou em quê (§19.1).
     */
    private WorkLog requireVisible(UUID id) {
        WorkLog workLog =
                repository
                        .findById(id)
                        .orElseThrow(() -> EntityNotFoundException.of(WorkLog.class, id));
        if (!ownershipPolicy.isVisible(workLog)) {
            throw EntityNotFoundException.of(WorkLog.class, id);
        }
        return workLog;
    }

    private void assertVersion(WorkLog workLog, long expected) {
        if (workLog.getVersion() != null && workLog.getVersion() != expected) {
            throw BusinessRuleException.versionConflict(ENTITY_TYPE, expected); // RN-004
        }
    }

    private WorkLogResponse toResponse(WorkLog workLog) {
        TicketWorkLogRefResponse ticket = ticketService.getRefForWorkLog(workLog.getTicketId());
        CategoryResponse category = categoryService.getById(workLog.getCategoryId());
        return mapper.toResponse(
                workLog,
                new WorkLogTicketResponse(ticket.id(), ticket.key(), ticket.title()),
                new WorkLogCategoryResponse(category.id(), category.name(), category.color()),
                userService.summaryOf(workLog.getUserId()),
                tagLinkService.findByWorkLogId(workLog.getId()));
    }

    /** Chaves dos tickets da página, uma consulta por ticket distinto — nunca por linha (§20). */
    private Map<UUID, String> ticketKeysOf(List<WorkLog> workLogs) {
        Map<UUID, String> keys = new LinkedHashMap<>();
        workLogs.forEach(
                workLog -> keys.computeIfAbsent(workLog.getTicketId(), ticketService::getKeyById));
        return keys;
    }

    /** As categorias do tenant cabem em uma consulta; resolvê-las por linha seria N+1. */
    private Map<UUID, String> categoryNames() {
        Map<UUID, String> names = new HashMap<>();
        categoryService
                .list(null, null)
                .forEach(category -> names.put(category.id(), category.name()));
        return names;
    }

    private Map<String, Object> describe(WorkLog workLog) {
        Map<String, Object> state = new HashMap<>();
        state.put("ticketId", workLog.getTicketId());
        state.put("categoryId", workLog.getCategoryId());
        state.put("workDate", workLog.getWorkDate().toString());
        state.put("startedAt", workLog.getStartedAt().toString());
        state.put("endedAt", workLog.getEndedAt().toString());
        state.put("pausedMinutes", workLog.getPausedMinutes());
        // §18: toda edição registra os valores anteriores de netMinutes e billableMinutes. Em uma
        // disputa, a pergunta é "esse registro sempre teve 150 minutos?".
        state.put("netMinutes", workLog.getNetMinutes());
        state.put("billableMinutes", workLog.billableMinutes());
        state.put("billable", workLog.isBillable());
        state.put("contractPeriodId", workLog.getContractPeriodId());
        return state;
    }
}
