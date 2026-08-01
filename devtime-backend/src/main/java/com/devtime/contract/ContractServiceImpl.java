package com.devtime.contract;

import com.devtime.audit.AuditService;
import com.devtime.category.CategoryService;
import com.devtime.client.ClientService;
import com.devtime.client.dto.ClientResponses.ClientResponse;
import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractExceptions;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.OveragePolicy;
import com.devtime.contract.domain.PeriodPlan;
import com.devtime.contract.domain.PeriodSpec;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.domain.RolloverPolicy;
import com.devtime.contract.dto.ContractRequests.ContractCreateRequest;
import com.devtime.contract.dto.ContractRequests.ContractTransitionRequest;
import com.devtime.contract.dto.ContractRequests.ContractUpdateRequest;
import com.devtime.contract.dto.ContractResponses.ContractActivationResponse;
import com.devtime.contract.dto.ContractResponses.ContractClientResponse;
import com.devtime.contract.dto.ContractResponses.ContractHistoryAggregates;
import com.devtime.contract.dto.ContractResponses.ContractHistoryPeriod;
import com.devtime.contract.dto.ContractResponses.ContractHistoryResponse;
import com.devtime.contract.dto.ContractResponses.ContractListItemResponse;
import com.devtime.contract.dto.ContractResponses.ContractPeriodResponse;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.contract.dto.ContractResponses.ContractTransitionResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.pagination.PageRequestFactory;
import com.devtime.shared.pagination.PageResponse;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de contrato (spec 004 §6).
 *
 * <p>A ordem de {@link #create} segue exatamente a §6.1 e é normativa (BR-062). Nenhum período é
 * gerado em {@code DRAFT}: um contrato em elaboração pode ter datas e políticas alteradas
 * livremente, e gerar períodos antes da ativação criaria estrutura a ser destruída a cada edição,
 * além de abrir a possibilidade de horas em contrato não vigente.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ContractServiceImpl implements ContractService {

    private static final int MIN_REASON_LENGTH = 10; // RN-215
    private static final int PREVIEW_PERIODS = 3;
    private static final int MAX_GAP_PERIODS = 36;
    private static final String ENTITY_TYPE = "Contract";

    private final ContractRepository repository;
    private final ContractPeriodRepository periodRepository;
    private final ContractMapper mapper;
    private final ContractStateMachine stateMachine;
    private final ContractCodeGenerator codeGenerator;
    private final ContractTypeCoherenceValidator coherenceValidator;
    private final ContractChangeGuards changeGuards;
    private final PeriodGenerator periodGenerator;
    private final PeriodContiguityValidator contiguityValidator;
    private final ClientService clientService;
    private final CategoryService categoryService;
    private final AuditService auditService;
    private final PageRequestFactory pageRequestFactory;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'CONTRACT_VIEW')")
    public PageResponse<ContractListItemResponse> search(
            UUID clientId,
            ContractStatus status,
            ContractType type,
            String search,
            Pageable pageable) {
        Pageable validated = pageRequestFactory.validate(pageable); // RN-012
        Page<Contract> page =
                repository.findAll(
                        ContractSpecifications.matching(clientId, status, type, search), validated);
        return PageResponse.of(page, this::toListItem);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'CONTRACT_VIEW')")
    public ContractResponse getById(UUID id) {
        return toDetail(require(id), List.of());
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CONTRACT_CREATE')")
    public ContractResponse create(ContractCreateRequest request) {
        // Passo 3 — RN-201/RN-405: o cliente precisa existir no tenant e estar ACTIVE.
        ClientResponse client = clientService.getActiveForContract(request.clientId());

        RolloverPolicy rolloverPolicy =
                request.rolloverPolicy() == null ? RolloverPolicy.NONE : request.rolloverPolicy();
        short billingDay =
                (short)
                        (request.billingDay() == null
                                ? request.startDate().getDayOfMonth()
                                : request.billingDay());

        // Passos 4 a 7 — INV-CTR-02/03/04, RN-202, RN-203, RN-204.
        coherenceValidator.assertCoherent(
                request.type(),
                request.monthlyMinutes(),
                rolloverPolicy,
                request.rolloverCapMinutes(),
                billingDay,
                request.startDate(),
                request.endDate());

        if (request.defaultCategoryId() != null) {
            // BR-048: a categoria referenciada precisa pertencer ao mesmo tenant e estar ativa.
            categoryService.requireActive(request.defaultCategoryId());
        }

        Contract contract = new Contract();
        contract.setClientId(request.clientId());
        // Passo 8 — INV-CTR-01: código informado ou sequencial.
        contract.setCode(resolveCode(request.code()));
        contract.setName(request.name().trim());
        contract.setDescription(request.description());
        contract.setType(request.type());
        contract.setStatus(ContractStatus.DRAFT); // Passo 9
        contract.setMonthlyMinutes(request.monthlyMinutes());
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setBillingDay(billingDay);
        contract.setRolloverPolicy(rolloverPolicy);
        contract.setRolloverCapMinutes(request.rolloverCapMinutes());
        contract.setRolloverExpiryPeriods(
                (short)
                        (request.rolloverExpiryPeriods() == null
                                ? 1
                                : request.rolloverExpiryPeriods()));
        contract.setOveragePolicy(
                request.overagePolicy() == null ? OveragePolicy.WARN : request.overagePolicy());
        contract.setHourlyRate(request.hourlyRate());
        // contracts.md §5: overageRate assume hourlyRate quando não informada.
        contract.setOverageRate(
                request.overageRate() == null ? request.hourlyRate() : request.overageRate());
        contract.setCurrency(request.currency() == null ? "BRL" : request.currency());
        contract.setAutoRenew(request.autoRenew() == null || request.autoRenew());
        contract.setProrateFirstPeriod(
                request.prorateFirstPeriod() == null || request.prorateFirstPeriod());
        contract.setNotificationThresholds(toShortArray(request.notificationThresholds()));
        contract.setDefaultCategoryId(request.defaultCategoryId());
        contract.setNotes(request.notes());

        Contract saved = repository.save(contract);
        auditService.record(
                "CONTRACT_CREATED",
                ENTITY_TYPE,
                saved.getId(),
                Map.of(),
                Map.of(
                        "code", saved.getCode(),
                        "type", saved.getType().name(),
                        "monthlyMinutes", String.valueOf(saved.getMonthlyMinutes()),
                        "startDate", saved.getStartDate().toString())); // RN-006

        log.info(
                "contrato criado contractId={} code={} type={} clientId={}",
                saved.getId(),
                saved.getCode(),
                saved.getType(),
                client.id());
        // A prévia acompanha a criação para que o usuário confira o ciclo antes de ativar.
        return toDetail(saved, previewOf(saved, PREVIEW_PERIODS));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CONTRACT_UPDATE')")
    public ContractResponse update(UUID id, ContractUpdateRequest request) {
        Contract contract = require(id);
        assertVersion(contract, request.version()); // RN-004
        assertNotTerminal(contract);

        boolean applyToCurrentPeriod = Boolean.TRUE.equals(request.applyToCurrentPeriod());
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();

        if (request.monthlyMinutes() != null
                && !request.monthlyMinutes().equals(contract.getMonthlyMinutes())) {
            changeGuards.assertMonthlyMinutesChangeAllowed(
                    contract, applyToCurrentPeriod); // RN-207
            before.put("monthlyMinutes", contract.getMonthlyMinutes());
            after.put("monthlyMinutes", request.monthlyMinutes());
            contract.setMonthlyMinutes(request.monthlyMinutes());
            if (applyToCurrentPeriod) {
                applyMonthlyMinutesToOpenPeriod(contract);
            }
        }
        if (request.billingDay() != null && request.billingDay() != contract.getBillingDay()) {
            changeGuards.assertBillingDayChangeAllowed(contract); // RN-208
            before.put("billingDay", (int) contract.getBillingDay());
            after.put("billingDay", request.billingDay());
            contract.setBillingDay(request.billingDay().shortValue());
        }

        RolloverPolicy rolloverPolicy =
                request.rolloverPolicy() == null
                        ? contract.getRolloverPolicy()
                        : request.rolloverPolicy();
        Integer rolloverCap =
                request.rolloverCapMinutes() == null
                        ? contract.getRolloverCapMinutes()
                        : request.rolloverCapMinutes();
        coherenceValidator.assertCoherent(
                contract.getType(),
                contract.getMonthlyMinutes(),
                rolloverPolicy,
                rolloverCap,
                contract.getBillingDay(),
                contract.getStartDate(),
                request.endDate() == null ? contract.getEndDate() : request.endDate());

        contract.setName(request.name().trim());
        contract.setDescription(request.description());
        contract.setEndDate(request.endDate());
        contract.setRolloverPolicy(rolloverPolicy);
        contract.setRolloverCapMinutes(rolloverCap);
        if (request.rolloverExpiryPeriods() != null) {
            contract.setRolloverExpiryPeriods(request.rolloverExpiryPeriods().shortValue());
        }
        if (request.overagePolicy() != null) {
            contract.setOveragePolicy(request.overagePolicy());
        }
        contract.setHourlyRate(request.hourlyRate());
        contract.setOverageRate(
                request.overageRate() == null ? request.hourlyRate() : request.overageRate());
        if (request.autoRenew() != null) {
            contract.setAutoRenew(request.autoRenew());
        }
        if (request.notificationThresholds() != null) {
            contract.setNotificationThresholds(toShortArray(request.notificationThresholds()));
        }
        if (request.defaultCategoryId() != null) {
            categoryService.requireActive(request.defaultCategoryId());
        }
        contract.setDefaultCategoryId(request.defaultCategoryId());
        contract.setNotes(request.notes());

        auditService.record("CONTRACT_UPDATED", ENTITY_TYPE, id, before, after); // RN-006
        return toDetail(contract, List.of());
    }

    /**
     * RN-209 / INV-CTR-06: a ativação e o primeiro período ocorrem na <b>mesma transação</b>.
     *
     * <p>Um contrato {@code ACTIVE} sem período não teria onde alocar horas (RN-107); a atomicidade
     * é o que impede esse estado existir mesmo por um instante.
     */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CONTRACT_TRANSITION')")
    public ContractActivationResponse activate(UUID id) {
        Contract contract = require(id);
        stateMachine.assertCanTransition(contract.getStatus(), ContractStatus.ACTIVE); // ME-04

        // CX-16: o cliente precisa estar ACTIVE no instante da ativação.
        clientService.getActiveForContract(contract.getClientId()); // RN-201
        assertActivationComplete(contract);

        List<ContractPeriod> created = generatePeriods(contract, null, 0, 1, PeriodStatus.OPEN);
        contract.setStatus(ContractStatus.ACTIVE);
        clientService.adjustActiveContractsCount(contract.getClientId(), +1);

        ContractPeriod firstPeriod = created.get(0);
        auditService.record(
                "CONTRACT_STATUS_CHANGED",
                ENTITY_TYPE,
                id,
                Map.of("status", ContractStatus.DRAFT.name()),
                Map.of(
                        "status", ContractStatus.ACTIVE.name(),
                        "firstPeriodId", firstPeriod.getId().toString()));

        log.info(
                "contrato ativado contractId={} firstPeriodId={} contractedMinutes={}",
                id,
                firstPeriod.getId(),
                firstPeriod.getContractedMinutes());
        return new ContractActivationResponse(
                contract.getStatus(), mapper.toPeriodResponse(firstPeriod));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CONTRACT_TRANSITION')")
    public ContractTransitionResponse suspend(UUID id, ContractTransitionRequest request) {
        Contract contract = require(id);
        stateMachine.assertCanTransition(contract.getStatus(), ContractStatus.SUSPENDED);
        assertReason(request);

        // A guarda "nenhum cronômetro ativo" (contracts.md §8.2, DEVTIME-2212) pertence a
        // 009-timer: a tabela timers não existe nesta sprint. Registrado como pendência da sprint.
        contract.setStatus(ContractStatus.SUSPENDED);
        recordStatusChange(id, ContractStatus.ACTIVE, ContractStatus.SUSPENDED, request.reason());
        // O período aberto permanece aberto; apenas a geração de novos períodos é interrompida.
        return new ContractTransitionResponse(contract.getStatus(), List.of(), null);
    }

    /**
     * CE-ME-09: a retomada gera os períodos que faltaram durante a suspensão.
     *
     * <p>Sem eles haveria lacuna entre o último período e a data corrente, quebrando INV-PER-03 — e
     * uma hora lançada na lacuna não teria período (RN-107).
     */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CONTRACT_TRANSITION')")
    public ContractTransitionResponse resume(UUID id) {
        Contract contract = require(id);
        stateMachine.assertCanTransition(contract.getStatus(), ContractStatus.ACTIVE);
        clientService.getActiveForContract(contract.getClientId()); // RN-201

        List<ContractPeriod> generated = generateMissingPeriods(contract);
        contract.setStatus(ContractStatus.ACTIVE);
        auditService.record(
                "CONTRACT_STATUS_CHANGED",
                ENTITY_TYPE,
                id,
                Map.of("status", ContractStatus.SUSPENDED.name()),
                Map.of(
                        "status", ContractStatus.ACTIVE.name(),
                        "generatedPeriods", String.valueOf(generated.size())));
        return new ContractTransitionResponse(
                contract.getStatus(), mapper.toPeriodResponses(generated), null);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CONTRACT_TRANSITION')")
    public ContractTransitionResponse end(UUID id, ContractTransitionRequest request) {
        Contract contract = require(id);
        stateMachine.assertCanTransition(contract.getStatus(), ContractStatus.ENDED);

        LocalDate endDate =
                request == null || request.endDate() == null ? clock.today() : request.endDate();
        if (endDate.isBefore(contract.getStartDate())) {
            throw ContractExceptions.endDateInvalid(endDate); // DEVTIME-2213
        }

        contract.setEndDate(endDate);
        ContractPeriod truncated = truncateCurrentPeriod(contract, endDate); // RN-214
        contract.setStatus(ContractStatus.ENDED);
        clientService.adjustActiveContractsCount(contract.getClientId(), -1);

        recordStatusChange(
                id,
                ContractStatus.ACTIVE,
                ContractStatus.ENDED,
                request == null ? null : request.reason());
        return new ContractTransitionResponse(
                contract.getStatus(),
                List.of(),
                truncated == null ? null : mapper.toPeriodResponse(truncated));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CONTRACT_TRANSITION')")
    public ContractTransitionResponse cancel(UUID id, ContractTransitionRequest request) {
        Contract contract = require(id);
        stateMachine.assertCanTransition(contract.getStatus(), ContractStatus.CANCELLED);
        assertReason(request); // Justificativa obrigatória no cancelamento (§18 da spec)

        ContractPeriod truncated = null;
        if (contract.getStatus() != ContractStatus.DRAFT) {
            truncated = truncateCurrentPeriod(contract, clock.today());
            clientService.adjustActiveContractsCount(contract.getClientId(), -1);
        }
        ContractStatus previous = contract.getStatus();
        contract.setStatus(ContractStatus.CANCELLED);

        recordStatusChange(id, previous, ContractStatus.CANCELLED, request.reason());
        return new ContractTransitionResponse(
                contract.getStatus(),
                List.of(),
                truncated == null ? null : mapper.toPeriodResponse(truncated));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CONTRACT_DELETE')")
    public void delete(UUID id) {
        Contract contract = require(id);
        changeGuards.assertDeletable(contract); // RN-205
        repository.softDelete(id, clock.now(), tenantContext.currentUserId().orElse(null));
        auditService.record(
                "CONTRACT_DELETED", ENTITY_TYPE, id, Map.of("code", contract.getCode()), Map.of());
        log.info("contrato excluído contractId={} code={}", id, contract.getCode());
    }

    @Override
    @PreAuthorize("hasPermission(null, 'PERIOD_VIEW')")
    public ContractHistoryResponse history(UUID id, int periods) {
        Contract contract = require(id);
        List<ContractPeriod> all =
                periodRepository.findByContractIdOrderBySequence(contract.getId());
        List<ContractPeriod> window =
                all.size() <= periods ? all : all.subList(all.size() - periods, all.size());

        List<ContractHistoryPeriod> entries = window.stream().map(this::toHistoryPeriod).toList();
        int periodsWithOverage =
                (int) entries.stream().filter(entry -> entry.overageMinutes() > 0).count();
        return new ContractHistoryResponse(
                contract.getId(),
                entries,
                new ContractHistoryAggregates(
                        entries.size(),
                        periodsWithOverage,
                        entries.stream().mapToInt(ContractHistoryPeriod::overageMinutes).sum(),
                        entries.stream().mapToInt(ContractHistoryPeriod::carriedOutMinutes).sum()));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'CONTRACT_VIEW')")
    public ContractResponse getActiveForWorkLog(UUID contractId) {
        Contract contract = require(contractId);
        if (contract.getStatus() != ContractStatus.ACTIVE
                && contract.getStatus() != ContractStatus.SUSPENDED) {
            // RN-306: contrato encerrado ou cancelado não aceita registro de horas.
            throw ContractExceptions.invalidTransition(
                    contract.getStatus(),
                    ContractStatus.ACTIVE,
                    stateMachine.availableTransitions(contract.getStatus()));
        }
        return toDetail(contract, List.of());
    }

    /** Interface pública para {@code 007}: caminho inverso da chave do ticket (RN-302, FA-15). */
    @Override
    @PreAuthorize("hasPermission(null, 'CONTRACT_VIEW')")
    public Optional<UUID> findIdByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        // O filtro de tenant torna o resultado vazio para código de outro tenant (ART-024).
        return repository.findByCode(code).map(Contract::getId);
    }

    /** Interface pública para {@code 007}: filtro de tickets por cliente (IMP-02). */
    @Override
    @PreAuthorize("hasPermission(null, 'CONTRACT_VIEW')")
    public List<UUID> findIdsByClient(UUID clientId) {
        return repository.findByClientId(clientId).stream().map(Contract::getId).toList();
    }

    /**
     * Interface pública para {@code 007} e {@code 008} (ver {@link ContractService#getRefById}).
     */
    @Override
    @PreAuthorize("hasPermission(null, 'CONTRACT_VIEW')")
    public com.devtime.contract.dto.ContractResponses.ContractRefResponse getRefById(
            UUID contractId) {
        Contract contract = require(contractId);
        var client = clientService.getRefById(contract.getClientId());
        return new com.devtime.contract.dto.ContractResponses.ContractRefResponse(
                contract.getId(),
                contract.getCode(),
                contract.getName(),
                contract.getStatus().name(),
                acceptsWorkLogs(contract.getStatus()),
                new ContractClientResponse(client.id(), client.name(), client.color()));
    }

    /**
     * Interface pública para {@code 008} e {@code 009} (ver {@link ContractService#getWorkLogRef}).
     *
     * <p>Aplica RN-306 aqui, e não na feature consumidora, porque a regra é do contrato: é o estado
     * dele que decide se o registro é aceito. Duplicar a decisão em {@code 008} e {@code 009}
     * criaria dois pontos que divergiriam na primeira mudança de estado do contrato.
     */
    @Override
    @PreAuthorize("hasPermission(null, 'CONTRACT_VIEW')")
    public com.devtime.contract.dto.ContractResponses.ContractWorkLogRefResponse getWorkLogRef(
            UUID contractId) {
        Contract contract = require(contractId);
        if (!acceptsWorkLogs(contract.getStatus())) {
            throw ContractExceptions.notAcceptingWork(contract.getStatus().name()); // RN-306
        }
        return new com.devtime.contract.dto.ContractResponses.ContractWorkLogRefResponse(
                contract.getId(),
                contract.getCode(),
                contract.getName(),
                contract.getClientId(),
                contract.getStatus().name(),
                true,
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getOveragePolicy().name(),
                contract.getDefaultCategoryId(),
                contract.getCurrency());
    }

    /** RN-306: apenas {@code ACTIVE} e {@code SUSPENDED} aceitam registro de horas. */
    private boolean acceptsWorkLogs(ContractStatus status) {
        return status == ContractStatus.ACTIVE || status == ContractStatus.SUSPENDED;
    }

    // ── Apoio ───────────────────────────────────────────────────────────────────────────────

    /** Passos 1 a 10 da §6.2, materializando o plano em entidades persistidas. */
    private List<ContractPeriod> generatePeriods(
            Contract contract,
            LocalDate previousEndDate,
            int previousSequence,
            int count,
            PeriodStatus status) {
        PeriodSpec spec = specOf(contract);
        List<PeriodPlan> plans =
                previousEndDate == null
                        ? periodGenerator.generate(spec, count)
                        : periodGenerator.generateAfter(
                                spec, previousEndDate, previousSequence, count);

        // Passo 10: contiguidade verificada antes de qualquer escrita (RN-216).
        contiguityValidator.assertContiguous(contract.getId(), previousEndDate, plans);

        List<ContractPeriod> created = new ArrayList<>();
        for (PeriodPlan plan : plans) {
            ContractPeriod period = new ContractPeriod();
            period.setContractId(contract.getId());
            period.setSequence(plan.sequence());
            period.setLabel(plan.label());
            period.setStartDate(plan.startDate());
            period.setEndDate(plan.endDate());
            period.setStatus(status);
            period.setContractedMinutes(plan.contractedMinutes());
            // Passo 9: congela os valores do contrato no período (§6.7 de entities.md).
            period.setHourlyRateSnapshot(contract.getHourlyRate());
            period.setOverageRateSnapshot(contract.getOverageRate());
            period.setCurrency(contract.getCurrency());
            created.add(periodRepository.save(period));

            auditService.recordSystemAction(
                    "PERIOD_CREATED",
                    "ContractPeriod",
                    created.get(created.size() - 1).getId(),
                    Map.of(
                            "sequence", String.valueOf(plan.sequence()),
                            "startDate", plan.startDate().toString(),
                            "endDate", plan.endDate().toString(),
                            "contractedMinutes", String.valueOf(plan.contractedMinutes())));
            log.info(
                    "período gerado contractId={} sequence={} {} a {} contractedMinutes={}",
                    contract.getId(),
                    plan.sequence(),
                    plan.startDate(),
                    plan.endDate(),
                    plan.contractedMinutes());
        }
        return created;
    }

    /** CE-ME-09: fecha a lacuna entre o último período e a data corrente. */
    private List<ContractPeriod> generateMissingPeriods(Contract contract) {
        Optional<ContractPeriod> last = periodRepository.findLastByContractId(contract.getId());
        if (last.isEmpty()) {
            return List.of();
        }
        ContractPeriod previous = last.get();
        LocalDate today = clock.today();
        if (!previous.getEndDate().isBefore(today)) {
            return List.of(); // O período corrente ainda cobre hoje: não há lacuna.
        }
        List<ContractPeriod> generated = new ArrayList<>();
        ContractPeriod cursor = previous;
        int guard = 0;
        while (cursor.getEndDate().isBefore(today) && guard++ < MAX_GAP_PERIODS) {
            List<ContractPeriod> next =
                    generatePeriods(
                            contract,
                            cursor.getEndDate(),
                            cursor.getSequence(),
                            1,
                            PeriodStatus.SCHEDULED);
            if (next.isEmpty()) {
                break; // endDate do contrato alcançada (RN-214).
            }
            cursor = next.get(0);
            generated.add(cursor);
        }
        // O último período gerado passa a ser o corrente, desde que não haja outro aberto.
        if (!generated.isEmpty()
                && periodRepository.findOpenByContractId(contract.getId()).isEmpty()) {
            generated.get(generated.size() - 1).setStatus(PeriodStatus.OPEN);
        }
        return generated;
    }

    /** RN-214: o período corrente termina junto com a vigência. */
    private ContractPeriod truncateCurrentPeriod(Contract contract, LocalDate endDate) {
        return periodRepository
                .findOpenByContractId(contract.getId())
                .filter(period -> period.getEndDate().isAfter(endDate))
                .filter(period -> !period.getStartDate().isAfter(endDate))
                .map(
                        period -> {
                            period.setEndDate(endDate);
                            return period;
                        })
                .orElse(null);
    }

    private void applyMonthlyMinutesToOpenPeriod(Contract contract) {
        periodRepository
                .findOpenByContractId(contract.getId())
                .ifPresent(period -> period.setContractedMinutes(contract.getMonthlyMinutes()));
    }

    private PeriodSpec specOf(Contract contract) {
        return new PeriodSpec(
                contract.getType(),
                contract.getMonthlyMinutes(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getBillingDay(),
                contract.isProrateFirstPeriod());
    }

    private List<PeriodPlan> previewOf(Contract contract, int count) {
        return periodGenerator.generate(specOf(contract), count);
    }

    private String resolveCode(String requestedCode) {
        if (requestedCode == null || requestedCode.isBlank()) {
            return codeGenerator.next();
        }
        String code = requestedCode.trim();
        if (repository.existsByCode(code)) {
            throw new BusinessRuleException(
                    com.devtime.shared.error.ErrorCode.CONTRACT_CODE_DUPLICATED,
                    Map.of("field", "code"),
                    "Código de contrato já existe") {};
        }
        return code;
    }

    private void assertActivationComplete(Contract contract) {
        if (contract.getType() == ContractType.MONTHLY_HOURS
                && contract.getMonthlyMinutes() == null) {
            throw ContractExceptions.activationIncomplete(
                    "monthlyMinutes é obrigatório para ativar um contrato mensal");
        }
        if (contract.getStartDate() == null) {
            throw ContractExceptions.activationIncomplete("startDate é obrigatória para ativar");
        }
    }

    private void assertReason(ContractTransitionRequest request) {
        if (request == null
                || request.reason() == null
                || request.reason().trim().length() < MIN_REASON_LENGTH) {
            throw ContractExceptions.justificationRequired(); // RN-215
        }
    }

    private void assertNotTerminal(Contract contract) {
        if (contract.getStatus().isTerminal()) {
            throw ContractExceptions.invalidTransition(
                    contract.getStatus(), contract.getStatus(), java.util.Set.of());
        }
    }

    private void assertVersion(Contract contract, long expected) {
        if (contract.getVersion() != null && contract.getVersion() != expected) {
            throw BusinessRuleException.versionConflict(ENTITY_TYPE, expected); // RN-004
        }
    }

    private void recordStatusChange(
            UUID id, ContractStatus from, ContractStatus to, String reason) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", to.name());
        if (reason != null) {
            after.put("reason", reason);
        }
        auditService.record(
                "CONTRACT_STATUS_CHANGED", ENTITY_TYPE, id, Map.of("status", from.name()), after);
        log.info("transição executada contractId={} de={} para={}", id, from, to);
    }

    private Contract require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> EntityNotFoundException.of(Contract.class, id));
    }

    private Short[] toShortArray(List<Integer> values) {
        List<Integer> source = values == null ? List.of(50, 80, 100) : values;
        return source.stream().map(Integer::shortValue).toArray(Short[]::new);
    }

    private ContractHistoryPeriod toHistoryPeriod(ContractPeriod period) {
        // RN-218/RN-220/RN-221: as fórmulas canônicas são de 011; aqui apenas se reapresentam os
        // valores já persistidos, sem recalcular saldo (fronteira de §4 da spec).
        int available =
                period.getContractedMinutes()
                        + period.getCarriedInMinutes()
                        + period.getAdjustmentMinutes();
        int remaining = available - period.getConsumedMinutes();
        return new ContractHistoryPeriod(
                period.getSequence(),
                period.getLabel(),
                period.getStatus(),
                period.getContractedMinutes(),
                period.getCarriedInMinutes(),
                period.getAdjustmentMinutes(),
                period.getConsumedMinutes(),
                remaining,
                Math.max(0, -remaining),
                period.getCarriedOutMinutes());
    }

    private ContractListItemResponse toListItem(Contract contract) {
        return new ContractListItemResponse(
                contract.getId(),
                contract.getCode(),
                contract.getName(),
                clientOf(contract),
                contract.getType(),
                contract.getStatus(),
                contract.getMonthlyMinutes(),
                contract.getStartDate(),
                contract.getEndDate(),
                currentPeriodOf(contract),
                contract.getVersion() == null ? 0L : contract.getVersion());
    }

    private ContractResponse toDetail(Contract contract, List<PeriodPlan> preview) {
        ContractResponse base = mapper.toResponse(contract);
        boolean financial =
                tenantContext.currentPermissions().contains(Permission.CONTRACT_VIEW_FINANCIAL);
        return new ContractResponse(
                base.id(),
                base.code(),
                base.name(),
                base.description(),
                clientOf(contract),
                base.type(),
                base.status(),
                base.monthlyMinutes(),
                base.startDate(),
                base.endDate(),
                base.billingDay(),
                base.rolloverPolicy(),
                base.rolloverCapMinutes(),
                base.rolloverExpiryPeriods(),
                base.overagePolicy(),
                // SG-03: sem CONTRACT_VIEW_FINANCIAL, o valor não sai do backend.
                financial ? base.hourlyRate() : null,
                financial ? base.overageRate() : null,
                base.currency(),
                base.autoRenew(),
                base.prorateFirstPeriod(),
                base.notificationThresholds(),
                base.defaultCategoryId(),
                base.notes(),
                currentPeriodOf(contract),
                mapper.toPreviewItems(preview),
                base.version(),
                stateMachine.availableTransitions(contract.getStatus()).stream()
                        .map(Enum::name)
                        .toList(),
                availableActions(contract));
    }

    private ContractClientResponse clientOf(Contract contract) {
        // O nome do cliente vem da interface pública de 003 (AR-02): nunca de uma junção com a
        // entidade Client, que criaria dependência entre features.
        ClientResponse client = clientService.getById(contract.getClientId());
        return new ContractClientResponse(client.id(), client.name(), client.color());
    }

    private ContractPeriodResponse currentPeriodOf(Contract contract) {
        return periodRepository
                .findOpenByContractId(contract.getId())
                .map(mapper::toPeriodResponse)
                .orElse(null);
    }

    /** ME-06: ações filtradas por estado e por permissão do requisitante. */
    private List<String> availableActions(Contract contract) {
        List<String> actions = new ArrayList<>();
        var permissions = tenantContext.currentPermissions();
        if (permissions.contains(Permission.CONTRACT_UPDATE)
                && !contract.getStatus().isTerminal()) {
            actions.add("UPDATE");
        }
        if (permissions.contains(Permission.CONTRACT_DELETE)
                && contract.getStatus() == ContractStatus.DRAFT) {
            actions.add("DELETE"); // RN-205
        }
        if (permissions.contains(Permission.CONTRACT_TRANSITION)) {
            stateMachine
                    .availableTransitions(contract.getStatus())
                    .forEach(target -> actions.add(actionOf(target)));
        }
        return List.copyOf(actions);
    }

    private String actionOf(ContractStatus target) {
        return switch (target) {
            case ACTIVE -> "ACTIVATE_OR_RESUME";
            case SUSPENDED -> "SUSPEND";
            case ENDED -> "END";
            case CANCELLED -> "CANCEL";
            case DRAFT -> "DRAFT";
        };
    }
}
