package com.devtime.ticket;

import com.devtime.audit.AuditService;
import com.devtime.category.CategoryService;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.ContractResponses.ContractRefResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.pagination.PageRequestFactory;
import com.devtime.shared.pagination.PageResponse;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.tag.TagLinkService;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketExceptions;
import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.domain.TicketType;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketRequests.TicketMoveContractRequest;
import com.devtime.ticket.dto.TicketRequests.TicketUpdateRequest;
import com.devtime.ticket.dto.TicketResponses.TicketMoveContractResponse;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import com.devtime.ticket.dto.TicketResponses.TicketSummaryResponse;
import com.devtime.ticket.event.TicketEvents.TicketContractMovedEvent;
import com.devtime.ticket.event.TicketEvents.TicketCreatedEvent;
import com.devtime.user.UserService;
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
 * Regras do ciclo de vida do ticket (spec 007 §6).
 *
 * <p>A ordem de {@link #create} segue exatamente a §6.1 da spec e <b>a ordem é normativa</b>
 * (BR-062). Duas decisões dessa ordem merecem registro:
 *
 * <ul>
 *   <li>O contrato é validado <b>antes de tudo</b> porque a chave deriva de {@code contract.code}:
 *       sem contrato válido não há identificador a gerar e todas as demais validações seriam
 *       trabalho descartado.
 *   <li>A reserva do número é o <b>penúltimo</b> passo. A sequência é um recurso não reciclável;
 *       consumi-la antes das validações produziria lacunas na numeração a cada requisição inválida
 *       — e uma lacuna em {@code CT-0001-7} levanta a pergunta "onde está o ticket 6?" com o
 *       cliente.
 * </ul>
 *
 * <p>§28 da spec: <b>{@code title}, {@code description} e {@code blockReason} nunca entram em
 * log</b> — são texto livre e podem conter dado pessoal de terceiros. A chave basta para
 * identificar o ticket em qualquer investigação, e é justamente o identificador que humanos usam.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TicketServiceImpl implements TicketService {

    private static final String ENTITY_TYPE = "Ticket";

    private final TicketRepository repository;
    private final TicketMapper mapper;
    private final TicketKeyBuilder keyBuilder;
    private final TicketNumberGenerator numberGenerator;
    private final TicketStateMachine stateMachine;
    private final AssigneeValidator assigneeValidator;
    private final ContractMoveGuard contractMoveGuard;
    private final TicketDeletionGuard deletionGuard;
    private final ContractService contractService;
    private final CategoryService categoryService;
    private final TagLinkService tagLinkService;
    private final UserService userService;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final PageRequestFactory pageRequestFactory;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public PageResponse<TicketSummaryResponse> search(
            UUID contractId,
            UUID clientId,
            List<TicketStatus> statuses,
            List<TicketType> types,
            List<TicketPriority> priorities,
            UUID assigneeId,
            UUID reporterId,
            List<UUID> tagIds,
            String search,
            Boolean overEstimate,
            Pageable pageable) {
        Pageable validated = pageRequestFactory.validate(pageable); // RN-012

        // Conjunção de etiquetas: o ticket precisa possuir todas as informadas (tickets.md §6).
        // Resolvida em consulta própria porque um IN vazio deve significar "nenhum ticket".
        List<UUID> ticketIdsWithTags =
                tagIds == null || tagIds.isEmpty()
                        ? null
                        : tagLinkService.ticketIdsWithAllTags(tagIds);

        // `tickets` não desnormaliza o cliente (database.md §7.7): o vínculo existe através do
        // contrato. O filtro por cliente resolve os contratos dele em uma consulta e recai sobre o
        // índice idx_tickets_tenant_contract — nunca filtra em memória (IMP-02).
        List<UUID> contractIds =
                clientId == null ? null : contractService.findIdsByClient(clientId);

        Page<Ticket> page =
                repository.findAll(
                        TicketSpecifications.withFilters(
                                contractId,
                                contractIds,
                                statuses,
                                types,
                                priorities,
                                assigneeId,
                                reporterId,
                                ticketIdsWithTags,
                                search,
                                overEstimate),
                        validated);

        List<Ticket> content = page.getContent();
        Map<UUID, List<TagOptionResponse>> tags = tagsOf(content);
        Map<UUID, String> contractCodes = contractsOf(content);
        return PageResponse.of(page, ticket -> toSummary(ticket, tags, contractCodes));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public TicketResponse getById(UUID id) {
        return toResponse(require(id));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public TicketResponse getByKey(String key) {
        return toResponse(requireByKey(key));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TICKET_CREATE')")
    public TicketResponse create(TicketCreateRequest request) {
        // §6.1 passos 3 e 4 — RN-301, RN-306. getActiveForWorkLog devolve 404 para contrato de
        // outro tenant e 422 para contrato que não aceita registros.
        ContractRefResponse contract = requireContractAcceptingWork(request.contractId());

        String title = requireTitle(request.title()); // passo 5 — RN-303
        assigneeValidator.assertAssignable(request.assigneeId()); // passo 6 — RN-304
        requireActiveCategory(request.defaultCategoryId()); // passo 7 — RN-104

        List<UUID> tagIds =
                tagLinkService.resolveTagIds(
                        new com.devtime.tag.dto.TagRequests.TagLinkRequest(
                                request.tagIds(), request.tagNames()));

        Ticket ticket = new Ticket();
        ticket.setContractId(contract.id());
        // passo 9 — RN-302: reserva atômica, logo após as validações e antes da persistência.
        ticket.setNumber(numberGenerator.nextFor(contract.id()));
        ticket.setTitle(title);
        ticket.setDescription(request.description());
        ticket.setType(request.type() == null ? TicketType.FEATURE : request.type());
        ticket.setPriority(request.priority() == null ? TicketPriority.MEDIUM : request.priority());
        ticket.setStatus(TicketStatus.BACKLOG); // passo 10 — ME-05
        ticket.setAssigneeId(request.assigneeId());
        // SG-07: reporterId vem do contexto autenticado, nunca do payload.
        ticket.setReporterId(tenantContext.requireUserId());
        ticket.setEstimatedMinutes(request.estimatedMinutes());
        ticket.setSpentMinutes(0); // SG-08: desnormalizado, ausente do DTO de escrita
        ticket.setBillableMinutes(0);
        ticket.setDueDate(request.dueDate());
        ticket.setDefaultCategoryId(request.defaultCategoryId());
        ticket.setExternalRef(request.externalRef());

        Ticket saved = repository.save(ticket);

        // passo 11 — o vínculo aplica RN-313 sobre o conjunto resultante.
        tagLinkService.replaceTicketTags(saved.getId(), tagIds);

        String key = keyBuilder.build(contract.code(), saved.getNumber());
        auditService.record(
                "TICKET_CREATED",
                ENTITY_TYPE,
                saved.getId(),
                Map.of(),
                Map.of(
                        "key", key,
                        "contractId", contract.id(),
                        "status", saved.getStatus().name()));
        events.publish(new TicketCreatedEvent(saved.getId(), key, contract.id()));

        // §28: nem título nem descrição em log.
        log.info(
                "ticket criado ticketId={} key={} contractId={}",
                saved.getId(),
                key,
                contract.id());
        return toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize(
            "hasPermission(null, 'TICKET_UPDATE_ANY') or hasPermission(null, 'TICKET_UPDATE_OWN')")
    public TicketResponse update(UUID id, TicketUpdateRequest request) {
        Ticket ticket = require(id);
        assertVersion(ticket, request.version()); // RN-004
        assertCanEdit(ticket); // OWN-04 / OWN-08

        requireActiveCategory(request.defaultCategoryId()); // RN-104

        Map<String, Object> before = describe(ticket);
        // RN-011: number, key e reporterId estão ausentes do DTO — não há o que impedir aqui,
        // a imutabilidade é garantida pela ausência do campo no contrato (SG-07).
        ticket.setTitle(requireTitle(request.title())); // RN-303
        ticket.setDescription(request.description());
        ticket.setType(request.type());
        ticket.setPriority(request.priority());
        ticket.setEstimatedMinutes(request.estimatedMinutes());
        ticket.setDueDate(request.dueDate());
        ticket.setDefaultCategoryId(request.defaultCategoryId());
        ticket.setExternalRef(request.externalRef());

        if (request.tagIds() != null) {
            List<TagOptionResponse> previous = tagLinkService.findByTicketId(id);
            List<TagOptionResponse> current =
                    tagLinkService.replaceTicketTags(id, request.tagIds());
            if (!previous.equals(current)) {
                auditService.record(
                        "TICKET_TAGS_CHANGED",
                        ENTITY_TYPE,
                        id,
                        Map.of("tagIds", previous.stream().map(TagOptionResponse::id).toList()),
                        Map.of("tagIds", current.stream().map(TagOptionResponse::id).toList()));
            }
        }

        auditService.record("TICKET_UPDATED", ENTITY_TYPE, id, before, describe(ticket));
        return toResponse(ticket);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TICKET_UPDATE_ANY')")
    public TicketMoveContractResponse moveContract(UUID id, TicketMoveContractRequest request) {
        Ticket ticket = require(id);
        assertVersion(ticket, request.version()); // RN-004

        ContractRefResponse current = requireContract(ticket.getContractId());
        // RN-306: o destino precisa aceitar registros, senão o ticket nasceria inútil no destino.
        ContractRefResponse target = requireContractAcceptingWork(request.targetContractId());
        contractMoveGuard.assertMovable(ticket, current, target); // RN-305

        ticket.setContractId(target.id());
        // RN-011 / CP-06: number e chave permanecem. O ticket CT-0001-42 movido para CT-0002
        // continua sendo CT-0001-42 — a chave já circulou fora do sistema.
        String key = keyBuilder.build(current.code(), ticket.getNumber());

        auditService.record(
                "TICKET_CONTRACT_MOVED",
                ENTITY_TYPE,
                id,
                Map.of("contractId", current.id()),
                Map.of("contractId", target.id()),
                Map.of("key", key, "keyUnchanged", true, "confirmed", request.confirmed()));
        events.publish(
                new TicketContractMovedEvent(
                        id, key, current.id(), target.id(), current.code(), target.code()));

        // §28: WARN porque altera a que contrato o trabalho pertence. É raro, e é a primeira coisa
        // a verificar quando um ticket "sumiu" de um relatório.
        log.warn(
                "ticket movido de contrato ticketId={} key={} from={} to={}",
                id,
                key,
                current.code(),
                target.code());

        return new TicketMoveContractResponse(
                id,
                key,
                new com.devtime.ticket.dto.TicketResponses.TicketContractResponse(
                        target.id(),
                        target.code(),
                        target.name(),
                        target.status(),
                        target.acceptsWorkLogs()),
                "A chave do ticket permanece "
                        + key
                        + ": ela deriva do contrato de origem e é"
                        + " referência externa permanente.");
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TICKET_DELETE')")
    public void delete(UUID id) {
        Ticket ticket = require(id);
        deletionGuard.assertDeletable(ticket); // RN-307

        String key = keyOf(ticket);
        // INV-TAG-04: desvincular antes do soft delete mantém os contadores coerentes.
        tagLinkService.unlinkAllFromTicket(id);
        repository.softDelete(
                id, clock.now(), tenantContext.currentUserId().orElse(null)); // RN-003

        auditService.record(
                "TICKET_DELETED",
                ENTITY_TYPE,
                id,
                Map.of("key", key, "status", ticket.getStatus().name()),
                Map.of("deletedAt", clock.now().toString()));
        log.info("ticket excluído ticketId={} key={}", id, key);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public TicketResponse getForWorkLog(UUID ticketId) {
        Ticket ticket = require(ticketId);
        // RN-306: falha aqui, e não em 008, porque a regra é do ticket — é o contrato dele que
        // determina se o registro é aceito.
        requireContractAcceptingWork(ticket.getContractId());
        return toResponse(ticket);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public com.devtime.ticket.dto.TicketResponses.TicketWorkLogRefResponse getRefForWorkLog(
            UUID ticketId) {
        Ticket ticket = require(ticketId);
        // RN-306, na mesma ordem da §6.1 de 008: o ticket existe no tenant, depois o contrato dele
        // aceita registro. Quem responde é a feature dona do ticket.
        return toWorkLogRef(ticket, requireContractAcceptingWork(ticket.getContractId()));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public com.devtime.ticket.dto.TicketResponses.TicketWorkLogRefResponse getRef(UUID ticketId) {
        Ticket ticket = require(ticketId);
        return toWorkLogRef(ticket, requireContract(ticket.getContractId()));
    }

    private com.devtime.ticket.dto.TicketResponses.TicketWorkLogRefResponse toWorkLogRef(
            Ticket ticket, ContractRefResponse contract) {
        return new com.devtime.ticket.dto.TicketResponses.TicketWorkLogRefResponse(
                ticket.getId(),
                keyBuilder.build(contract.code(), ticket.getNumber()),
                ticket.getTitle(),
                contract.id(),
                contract.client() == null ? null : contract.client().id(),
                ticket.getDefaultCategoryId());
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public List<UUID> findIdsByContract(UUID contractId) {
        return repository.findIdsByContractId(contractId);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TICKET_VIEW')")
    public String getKeyById(UUID ticketId) {
        return keyOf(require(ticketId));
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private Ticket require(UUID id) {
        // ART-024: inexistente e de outro tenant produzem a mesma resposta.
        return repository
                .findById(id)
                .orElseThrow(() -> EntityNotFoundException.of(Ticket.class, id));
    }

    /**
     * FA-15 / CX-19: resolve a chave legível decompondo-a em (código do contrato, número).
     *
     * <p>Chave malformada, de contrato inexistente e de outro tenant produzem o mesmo {@code 404} —
     * distinguir permitiria sondar a existência de contratos alheios.
     */
    private Ticket requireByKey(String key) {
        return keyBuilder
                .parse(key)
                .flatMap(
                        parsed ->
                                contractService
                                        .findIdByCode(parsed.contractCode())
                                        .flatMap(
                                                contractId ->
                                                        repository.findByContractIdAndNumber(
                                                                contractId, parsed.number())))
                .orElseThrow(() -> EntityNotFoundException.of(Ticket.class, null));
    }

    private String requireTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.strip();
        if (title.length() < 3 || title.length() > 200) {
            throw TicketExceptions.titleInvalid(title.length()); // RN-303
        }
        return title;
    }

    /** RN-104: categoria padrão precisa existir e estar ativa no tenant. */
    private void requireActiveCategory(UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        try {
            categoryService.requireActive(categoryId);
        } catch (EntityNotFoundException notFoundOrInactive) {
            // CategoryService responde 404 para inexistente, de outro tenant ou inativa; o contrato
            // de tickets.md §13 exige DEVTIME-2104 no campo defaultCategoryId.
            throw TicketExceptions.categoryInvalid();
        }
    }

    private ContractRefResponse requireContract(UUID contractId) {
        if (contractId == null) {
            throw TicketExceptions.contractRequired(); // RN-301
        }
        return contractService.getRefById(contractId);
    }

    /** RN-301 + RN-306, na ordem da §6.1: existir no tenant, depois aceitar registros. */
    private ContractRefResponse requireContractAcceptingWork(UUID contractId) {
        ContractRefResponse contract = requireContract(contractId);
        if (!contract.acceptsWorkLogs()) {
            throw TicketExceptions.contractNotAcceptingWork(contract.status()); // RN-306
        }
        return contract;
    }

    /**
     * OWN-04 / OWN-08: {@code TICKET_UPDATE_ANY} dispensa ownership; sem ele, o requisitante
     * precisa ser relator ou responsável.
     */
    private void assertCanEdit(Ticket ticket) {
        if (tenantContext.currentPermissions().contains(Permission.TICKET_UPDATE_ANY)) {
            return;
        }
        UUID currentUserId = tenantContext.requireUserId();
        boolean own =
                currentUserId.equals(ticket.getReporterId())
                        || currentUserId.equals(ticket.getAssigneeId());
        if (!own) {
            throw new com.devtime.shared.error.OwnershipViolationException("Ticket");
        }
    }

    private void assertVersion(Ticket ticket, long expected) {
        if (ticket.getVersion() != null && ticket.getVersion() != expected) {
            throw BusinessRuleException.versionConflict(ENTITY_TYPE, expected); // RN-004
        }
    }

    private String keyOf(Ticket ticket) {
        return keyBuilder.build(
                contractService.getRefById(ticket.getContractId()).code(), ticket.getNumber());
    }

    private TicketResponse toResponse(Ticket ticket) {
        ContractRefResponse contract = contractService.getRefById(ticket.getContractId());
        return mapper.toResponse(
                ticket,
                keyBuilder.build(contract.code(), ticket.getNumber()),
                new com.devtime.ticket.dto.TicketResponses.TicketContractResponse(
                        contract.id(),
                        contract.code(),
                        contract.name(),
                        contract.status(),
                        contract.acceptsWorkLogs()),
                contract.client() == null
                        ? null
                        : new com.devtime.ticket.dto.TicketResponses.TicketClientResponse(
                                contract.client().id(),
                                contract.client().name(),
                                contract.client().color()),
                userService.summaryOf(ticket.getAssigneeId()),
                userService.summaryOf(ticket.getReporterId()),
                tagLinkService.findByTicketId(ticket.getId()),
                List.copyOf(
                        stateMachine.availableTransitions(
                                ticket.getStatus(), tenantContext.currentPermissions())));
    }

    /**
     * Etiquetas de todos os tickets da página em <b>uma</b> consulta.
     *
     * <p>Resolver por ticket produziria N+1 no caminho mais visitado da feature (§20 da spec).
     */
    private Map<UUID, List<TagOptionResponse>> tagsOf(List<Ticket> tickets) {
        return tagLinkService.findByTicketIds(tickets.stream().map(Ticket::getId).toList());
    }

    /** Códigos de contrato da página, em uma consulta por contrato distinto. */
    private Map<UUID, String> contractsOf(List<Ticket> tickets) {
        Map<UUID, String> codes = new LinkedHashMap<>();
        tickets.forEach(
                ticket ->
                        codes.computeIfAbsent(
                                ticket.getContractId(),
                                contractId -> contractService.getRefById(contractId).code()));
        return codes;
    }

    private TicketSummaryResponse toSummary(
            Ticket ticket,
            Map<UUID, List<TagOptionResponse>> tags,
            Map<UUID, String> contractCodes) {
        return mapper.toSummary(
                ticket,
                keyBuilder.build(
                        contractCodes.getOrDefault(ticket.getContractId(), ""), ticket.getNumber()),
                contractCodes.get(ticket.getContractId()),
                userService.summaryOf(ticket.getAssigneeId()),
                tags.getOrDefault(ticket.getId(), List.of()));
    }

    private Map<String, Object> describe(Ticket ticket) {
        Map<String, Object> state = new HashMap<>();
        state.put("title", ticket.getTitle());
        state.put("type", ticket.getType().name());
        state.put("priority", ticket.getPriority().name());
        state.put("estimatedMinutes", ticket.getEstimatedMinutes());
        state.put("dueDate", ticket.getDueDate() == null ? null : ticket.getDueDate().toString());
        state.put("defaultCategoryId", ticket.getDefaultCategoryId());
        state.put("externalRef", ticket.getExternalRef());
        return state;
    }
}
