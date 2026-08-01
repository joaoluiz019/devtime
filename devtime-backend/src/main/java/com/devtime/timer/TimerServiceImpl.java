package com.devtime.timer;

import com.devtime.audit.AuditService;
import com.devtime.category.CategoryService;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.ContractResponses.ContractWorkLogRefResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.error.OwnershipViolationException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.ticket.TicketService;
import com.devtime.ticket.dto.TicketResponses.TicketWorkLogRefResponse;
import com.devtime.timer.domain.Timer;
import com.devtime.timer.domain.TimerExceptions;
import com.devtime.timer.domain.TimerStatus;
import com.devtime.timer.dto.TimerRequests.TimerRecoverRequest;
import com.devtime.timer.dto.TimerRequests.TimerStartRequest;
import com.devtime.timer.dto.TimerRequests.TimerStopRequest;
import com.devtime.timer.dto.TimerRequests.TimerUpdateRequest;
import com.devtime.timer.dto.TimerResponses.AbandonedTimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerStopResponse;
import com.devtime.timer.dto.TimerResponses.TimerTicketResponse;
import com.devtime.timer.event.TimerEvents.TimerCompletedEvent;
import com.devtime.timer.event.TimerEvents.TimerDiscardedEvent;
import com.devtime.timer.event.TimerEvents.TimerForceStoppedEvent;
import com.devtime.timer.event.TimerEvents.TimerStartedEvent;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCreatedResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida do cronômetro (spec 009 §6).
 *
 * <p><b>RN-160 é a regra estruturante desta classe.</b> O encerramento monta o comando, delega a
 * {@code WorkLogService.createFromTimer} e só então marca o cronômetro como {@code COMPLETED}. Se a
 * geração do work log falhar por qualquer motivo — sobreposição (RN-102), saldo estourado (RN-231),
 * contrato encerrado (RN-306) —, a exceção propaga <b>antes</b> de qualquer alteração de estado e o
 * cronômetro permanece ativo. É o que sustenta PV-03: o tempo trabalhado nunca é descartado pelo
 * sistema por causa de um erro de configuração.
 *
 * <p>Para que isso funcione, o encerramento <b>não</b> abre transação própria de escrita antes da
 * validação: as alterações de estado ocorrem depois, na mesma transação em que o work log já foi
 * persistido. O caminho de erro nunca chega a tocá-las.
 *
 * <p>§28 / §19.1: <b>{@code description} nunca entra em log</b>, nem a razão de uma pausa.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TimerServiceImpl implements TimerService {

    private static final String ENTITY_TYPE = "Timer";

    private final TimerRepository repository;
    private final TimerMapper mapper;
    private final TimerStateMachine stateMachine;
    private final TimerPausePolicy pausePolicy;
    private final ActiveTimerPolicy activeTimerPolicy;
    private final AbandonedTimerPolicy abandonedTimerPolicy;
    private final TimerFailureAuditor failureAuditor;
    private final TimerQueryServiceImpl queryService;
    private final WorkLogService workLogService;
    private final TicketService ticketService;
    private final ContractService contractService;
    private final CategoryService categoryService;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    // ── Início ───────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public TimerResponse start(TimerStartRequest request, boolean stopCurrent) {
        UUID userId = tenantContext.requireUserId(); // OWN-05: nunca da requisição (SG-01)

        if (stopCurrent) {
            // RN-166 / CX-17: a troca de tarefa é atômica. O encerramento acontece primeiro e na
            // mesma transação — se ele falhar, a exceção propaga e o novo cronômetro não chega a
            // ser criado. Nada acontece pela metade.
            activeTimerPolicy.findActive(userId).ifPresent(current -> stopInternal(current, null));
        }
        activeTimerPolicy.assertNoActiveTimer(userId); // RN-150

        TicketWorkLogRefResponse ticket = ticketService.getRefForWorkLog(request.ticketId());
        ContractWorkLogRefResponse contract = contractService.getWorkLogRef(ticket.contractId());
        CategoryResponse category = resolveCategory(request.categoryId(), ticket, contract);

        Instant now = clock.now();
        Timer timer = new Timer();
        timer.setUserId(userId);
        timer.setTicketId(ticket.id());
        timer.setCategoryId(category.id());
        timer.setStatus(TimerStatus.RUNNING);
        // RN-152: os três campos de estado nascem juntos e sempre do servidor (SG-05).
        timer.setStartedAt(now);
        timer.setLastResumedAt(now);
        timer.setAccumulatedActiveSeconds(0);
        timer.setPausedMinutes(0);
        timer.setDescription(request.description());
        timer.setBillable(
                request.billable() == null ? category.billableByDefault() : request.billable());

        Timer saved = repository.save(timer);
        auditService.record(
                "TIMER_STARTED",
                ENTITY_TYPE,
                saved.getId(),
                Map.of(),
                Map.of("ticketId", saved.getTicketId(), "startedAt", now.toString()));
        events.publish(new TimerStartedEvent(saved.getId(), saved.getTicketId(), userId));

        log.info(
                "cronômetro iniciado timerId={} ticketKey={} userId={}",
                saved.getId(),
                ticket.key(),
                userId);
        return queryService.toResponse(saved);
    }

    // ── Edição durante a execução ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public TimerResponse update(TimerUpdateRequest request) {
        Timer timer = requireOwnActive();
        // RN-161: descobrir a natureza do trabalho durante a execução é normal, não exceção.
        if (request.ticketId() != null) {
            // CX-22: o ticket novo pode pertencer a contrato encerrado. A edição é permitida; é o
            // encerramento que falhará em RN-306, com a orientação de mover o ticket.
            timer.setTicketId(ticketService.getRef(request.ticketId()).id());
        }
        if (request.categoryId() != null) {
            timer.setCategoryId(categoryService.requireActive(request.categoryId()).id());
        }
        if (request.description() != null) {
            timer.setDescription(request.description());
        }
        if (request.billable() != null) {
            timer.setBillable(request.billable());
        }
        return queryService.toResponse(timer);
    }

    // ── Pausa e retomada ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public TimerResponse pause() {
        Timer timer = requireOwnActive();
        stateMachine.assertCanPause(timer); // RN-153
        pausePolicy.pause(timer); // RN-154

        auditService.record(
                "TIMER_PAUSED",
                ENTITY_TYPE,
                timer.getId(),
                Map.of("status", TimerStatus.RUNNING.name()),
                Map.of(
                        "status", TimerStatus.PAUSED.name(),
                        "accumulatedActiveSeconds", timer.getAccumulatedActiveSeconds()));
        log.debug("cronômetro pausado timerId={}", timer.getId());
        return queryService.toResponse(timer);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public TimerResponse resume() {
        Timer timer = requireOwnActive();
        stateMachine.assertCanResume(timer); // RN-155
        pausePolicy.resume(timer); // RN-156

        auditService.record(
                "TIMER_RESUMED",
                ENTITY_TYPE,
                timer.getId(),
                Map.of("status", TimerStatus.PAUSED.name()),
                Map.of(
                        "status", TimerStatus.RUNNING.name(),
                        "pausedMinutes", timer.getPausedMinutes()));
        log.debug(
                "cronômetro retomado timerId={} pausedMinutes={}",
                timer.getId(),
                timer.getPausedMinutes());
        return queryService.toResponse(timer);
    }

    // ── Encerramento ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public TimerStopResponse stop(TimerStopRequest request) {
        return stopInternal(requireOwnActive(), request.description());
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TIMER_STOP_ANY')")
    public TimerStopResponse forceStop(UUID timerId, TimerStopRequest request) {
        Timer timer = require(timerId);
        stateMachine.assertActive(timer);
        TimerStopResponse response = stopInternal(timer, request.description());

        // SG-07 / OWN-05: o dono é sempre notificado. Interferir no cronômetro de alguém sem que
        // essa pessoa saiba produziria um registro de horas que ela não reconhece como seu.
        events.publish(
                new TimerForceStoppedEvent(
                        timer.getId(),
                        timer.getUserId(),
                        timer.getTicketId(),
                        tenantContext.requireUserId()));
        auditService.record(
                "TIMER_FORCE_STOPPED",
                ENTITY_TYPE,
                timer.getId(),
                Map.of("status", TimerStatus.RUNNING.name()),
                Map.of("status", TimerStatus.COMPLETED.name(), "workLogId", timer.getWorkLogId()),
                Map.of("ownerId", timer.getUserId(), "stoppedBy", tenantContext.requireUserId()));
        log.warn(
                "cronômetro encerrado à força timerId={} ownerId={} stoppedBy={}",
                timer.getId(),
                timer.getUserId(),
                tenantContext.requireUserId());
        return response;
    }

    /**
     * Ordem da §6.1 da spec 009, <b>nesta sequência exata</b>.
     *
     * <p>O passo 3 (descrição) precede o passo 4 (fechar a pausa) porque fechar a pausa altera
     * estado persistido: rejeitar depois exigiria desfazer a alteração, e é o caminho de erro mais
     * frequente.
     */
    private TimerStopResponse stopInternal(Timer timer, String description) {
        stateMachine.assertActive(timer); // Passo 2

        // Passo 3 — RN-158. Verificado aqui e não só por Bean Validation porque o encerramento
        // interno de RN-166 não passa por um DTO.
        String stopDescription = description == null ? timer.getDescription() : description;
        if (stopDescription == null || stopDescription.strip().length() < 3) {
            // RN-160: o cronômetro permanece intocado — nada foi alterado até este ponto.
            auditFailure(timer, ErrorCode.WORKLOG_DESCRIPTION_INVALID.getCode());
            throw TimerExceptions.descriptionRequired(
                    stopDescription == null ? 0 : stopDescription.strip().length());
        }

        Instant stoppedAt = clock.now(); // Passo 5
        pausePolicy.closeForStop(timer, stoppedAt); // Passo 4

        // Passos 6 e 7 — o comando é montado a partir do estado do cronômetro e delegado a 008.
        // RN-159: nenhuma validação é reimplementada aqui (CP-14).
        WorkLogCreatedResponse created;
        try {
            created =
                    workLogService.createFromTimer(
                            timer.getId(),
                            timer.getTicketId(),
                            timer.getCategoryId(),
                            timer.getUserId(),
                            timer.getStartedAt(),
                            stoppedAt,
                            timer.getPausedMinutes(),
                            stopDescription,
                            timer.isBillable());
        } catch (BusinessRuleException violation) {
            // RN-160: a transação inteira é revertida, inclusive o fechamento da pausa do passo 4.
            // O cronômetro volta ao estado anterior e o usuário pode corrigir o ticket, o horário
            // ou pedir um ajuste de saldo — com o tempo preservado.
            auditFailure(timer, violation.getErrorCode().getCode());
            log.warn(
                    "falha no encerramento do cronômetro timerId={} code={} elapsedSeconds={}",
                    timer.getId(),
                    violation.getErrorCode().getCode(),
                    timer.elapsedSeconds(stoppedAt));
            throw violation;
        }

        // Passo 8 — só agora o estado muda (INV-TMR-04).
        timer.setStoppedAt(stoppedAt);
        timer.setWorkLogId(created.workLog().id());
        timer.setDescription(stopDescription);
        timer.setStatus(TimerStatus.COMPLETED);

        // Passo 9.
        auditService.record(
                "TIMER_COMPLETED",
                ENTITY_TYPE,
                timer.getId(),
                Map.of("status", TimerStatus.RUNNING.name()),
                Map.of(
                        "status", TimerStatus.COMPLETED.name(),
                        "workLogId", created.workLog().id(),
                        "netMinutes", created.workLog().netMinutes()));
        events.publish(
                new TimerCompletedEvent(
                        timer.getId(), created.workLog().id(), created.workLog().netMinutes()));
        log.info(
                "cronômetro encerrado timerId={} workLogId={} netMinutes={}",
                timer.getId(),
                created.workLog().id(),
                created.workLog().netMinutes());

        return new TimerStopResponse(
                queryService.toResponse(timer),
                created.workLog(),
                created.balance(),
                created.warnings());
    }

    // ── Descarte ─────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public void discard(boolean confirmed) {
        if (!confirmed) {
            throw TimerExceptions.discardNotConfirmed(); // RN-162
        }
        Timer timer = requireOwnActive();
        stateMachine.assertActive(timer);
        discardInternal(timer, "TIMER_DISCARDED");
    }

    @Override
    @Transactional
    public int discardForUser(UUID userId) {
        List<Timer> active = repository.findActiveByUserInTenant(userId);
        // CX-19: PAUSED é descartado igualmente. O tempo fica apenas em auditoria (CE-ME-06).
        active.forEach(timer -> discardInternal(timer, "TIMER_DISCARDED_MEMBER_REMOVED"));
        return active.size();
    }

    private void discardInternal(Timer timer, String auditAction) {
        int elapsedSeconds = timer.elapsedSeconds(clock.now());
        timer.setStatus(TimerStatus.DISCARDED);
        // INV-TMR-05: descarte nunca produz work log; workLogId permanece nulo.

        // §18: o descarte registra QUANTO tempo foi descartado. É a única operação que destrói
        // trabalho registrado sem contrapartida, e a auditoria é o que permite responder "por que
        // faltam 3 horas naquela terça".
        auditService.record(
                auditAction,
                ENTITY_TYPE,
                timer.getId(),
                Map.of("status", TimerStatus.RUNNING.name(), "elapsedSeconds", elapsedSeconds),
                Map.of("status", TimerStatus.DISCARDED.name()),
                Map.of("discardedSeconds", elapsedSeconds));
        events.publish(new TimerDiscardedEvent(timer.getId(), timer.getUserId(), elapsedSeconds));
        log.warn(
                "cronômetro descartado timerId={} elapsedSeconds={}",
                timer.getId(),
                elapsedSeconds);
    }

    // ── Abandonados ──────────────────────────────────────────────────────────────────────────

    @Override
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public List<AbandonedTimerResponse> abandoned() {
        Instant now = clock.now();
        return repository.findAbandonedByUser(tenantContext.requireUserId()).stream()
                .map(
                        timer -> {
                            var ticket = ticketService.getRef(timer.getTicketId());
                            return mapper.toAbandoned(
                                    timer,
                                    new TimerTicketResponse(
                                            ticket.id(), ticket.key(), ticket.title()),
                                    now,
                                    abandonedTimerPolicy.recoverableUntil(timer));
                        })
                .toList();
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public TimerStopResponse recover(UUID timerId, TimerRecoverRequest request) {
        Timer timer = require(timerId);
        assertOwn(timer); // OWN-05
        stateMachine.assertRecoverable(timer);
        abandonedTimerPolicy.assertWithinRecoveryWindow(timer); // RN-165

        String description =
                request.description() == null ? timer.getDescription() : request.description();
        if (description == null || description.strip().length() < 3) {
            throw TimerExceptions.descriptionRequired(
                    description == null ? 0 : description.strip().length()); // RN-158
        }

        // CX-09 / CX-10: o endedAt informado passa por TODAS as validações de 008 — 25 horas é
        // rejeitado por RN-103 e período fechado por RN-121. Em qualquer falha, o cronômetro
        // permanece ABANDONED e continua recuperável dentro da janela (RN-160).
        WorkLogCreatedResponse created =
                workLogService.createFromTimer(
                        timer.getId(),
                        timer.getTicketId(),
                        timer.getCategoryId(),
                        timer.getUserId(),
                        timer.getStartedAt(),
                        request.endedAt(),
                        timer.getPausedMinutes(),
                        description,
                        timer.isBillable());

        timer.setStoppedAt(request.endedAt());
        timer.setWorkLogId(created.workLog().id());
        timer.setDescription(description);
        timer.setStatus(TimerStatus.COMPLETED);

        auditService.record(
                "TIMER_RECOVERED",
                ENTITY_TYPE,
                timer.getId(),
                Map.of("status", TimerStatus.ABANDONED.name()),
                Map.of(
                        "status", TimerStatus.COMPLETED.name(),
                        "workLogId", created.workLog().id()),
                Map.of("endedAt", request.endedAt().toString()));
        log.info(
                "cronômetro abandonado recuperado timerId={} workLogId={}",
                timer.getId(),
                created.workLog().id());

        return new TimerStopResponse(
                queryService.toResponse(timer),
                created.workLog(),
                created.balance(),
                created.warnings());
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    /** §18: delega a {@link TimerFailureAuditor}, que abre transação própria (RN-160). */
    private void auditFailure(Timer timer, String errorCode) {
        failureAuditor.recordStopFailure(
                timer.getId(), errorCode, timer.elapsedSeconds(clock.now()));
    }

    /** OWN-05: o cronômetro do usuário autenticado, em qualquer tenant (RN-150). */
    private Timer requireOwnActive() {
        return activeTimerPolicy
                .findActive(tenantContext.requireUserId())
                .orElseThrow(() -> EntityNotFoundException.of(Timer.class, null));
    }

    private Timer require(UUID timerId) {
        return repository
                .findById(timerId)
                .orElseThrow(() -> EntityNotFoundException.of(Timer.class, timerId));
    }

    /** OWN-05: a regra de propriedade mais restritiva do sistema — nem {@code MANAGER} opera. */
    private void assertOwn(Timer timer) {
        if (tenantContext.currentPermissions().contains(Permission.TIMER_STOP_ANY)) {
            return;
        }
        if (!tenantContext.requireUserId().equals(timer.getUserId())) {
            throw new OwnershipViolationException(ENTITY_TYPE);
        }
    }

    /** RN-104: a mesma cadeia do registro manual, resolvida por {@code 005}. */
    private CategoryResponse resolveCategory(
            UUID requestedCategoryId,
            TicketWorkLogRefResponse ticket,
            ContractWorkLogRefResponse contract) {
        if (requestedCategoryId != null) {
            return categoryService.requireActive(requestedCategoryId);
        }
        return categoryService
                .resolveForWorkLog(ticket.defaultCategoryId(), contract.defaultCategoryId(), null)
                .orElseThrow(TimerExceptions::categoryInvalid);
    }
}
