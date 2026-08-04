package com.devtime.tenant;

import com.devtime.audit.AuditService;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TimezoneValidator;
import com.devtime.tenant.MemberRemovalPorts.PeriodClosingStateSource;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.domain.Tenant;
import com.devtime.tenant.domain.TenantExceptions;
import com.devtime.tenant.domain.TenantStatus;
import com.devtime.tenant.dto.TenantCommands.NewTenant;
import com.devtime.tenant.dto.TenantRequests.TenantCancelRequest;
import com.devtime.tenant.dto.TenantRequests.TenantSettingsRequest;
import com.devtime.tenant.dto.TenantRequests.TenantUpdateRequest;
import com.devtime.tenant.dto.TenantResponses.TenantCancelResponse;
import com.devtime.tenant.dto.TenantResponses.TenantResponse;
import com.devtime.tenant.dto.TenantSettings;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import com.devtime.tenant.dto.TenantViews.SessionSnapshot;
import com.devtime.tenant.dto.TenantViews.TenantOption;
import com.devtime.tenant.dto.TenantViews.TenantState;
import com.devtime.tenant.dto.TenantViews.TenantView;
import com.devtime.tenant.event.TenantEvents.TenantCancelledEvent;
import com.devtime.tenant.event.TenantEvents.TenantSettingsUpdatedEvent;
import com.devtime.user.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link TenantService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TenantServiceImpl implements TenantService {

    private static final String DEFAULT_LOCALE = "pt-BR";
    private static final String DEFAULT_CURRENCY = "BRL";
    private static final String DEFAULT_SETTINGS = "{}";
    private static final String DEFAULT_PLAN_CODE = "FREE"; // OB-04: ignorado no MVP

    static final String ENTITY_TYPE = "TENANT";
    static final String ACTION_UPDATED = "TENANT_UPDATED";
    static final String ACTION_SETTINGS_UPDATED = "TENANT_SETTINGS_UPDATED";
    static final String ACTION_CANCELLED = "TENANT_CANCELLED";
    static final String ACTION_PURGED = "TENANT_PURGED";

    /** RN-008: retenção antes da purga. */
    static final Duration RETENTION = Duration.ofDays(30);

    /** BR-186: teto de tenants processados por execução do job de purga. */
    static final int PURGE_BATCH_SIZE = 50;

    private final TenantRepository repository;
    private final MembershipRepository membershipRepository;
    private final SlugGenerator slugGenerator;
    private final TenantMapper mapper;
    private final TenantSettingsService settingsService;
    private final TenantSettingsValidator settingsValidator;
    private final TenantSettingsWriter settingsWriter;
    private final TimezoneValidator timezoneValidator;
    private final UserAccountService userAccountService;
    private final PeriodClosingStateSource periodClosingStateSource;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantContext tenantContext;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public String provision(UUID tenantId, NewTenant command) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName(command.name());
        tenant.setSlug(slugGenerator.generate(command.name(), repository::existsBySlug));
        tenant.setEmail(command.contactEmail());
        tenant.setTimezone(command.timezone());
        tenant.setLocale(DEFAULT_LOCALE);
        tenant.setCurrency(DEFAULT_CURRENCY);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPlanCode(DEFAULT_PLAN_CODE);
        tenant.setSettings(DEFAULT_SETTINGS);
        return repository.save(tenant).getSlug();
    }

    @Override
    public Optional<TenantView> find(UUID tenantId) {
        return tenantId == null
                ? Optional.empty()
                : repository.findById(tenantId).map(this::toView);
    }

    @Override
    public TenantView require(UUID tenantId) {
        return find(tenantId).orElseThrow(() -> EntityNotFoundException.of(Tenant.class, tenantId));
    }

    @Override
    public List<TenantOption> optionsFor(UUID userId) {
        List<Membership> memberships = membershipRepository.findActiveByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        // Uma consulta para os vínculos e outra para as organizações, em lote. A alternativa —
        // navegar de Membership para Tenant por associação — produziria uma consulta por vínculo
        // (QY-03).
        List<Tenant> tenants =
                repository.findAllByIdIn(
                        memberships.stream().map(Membership::getTenantId).toList());
        return memberships.stream()
                .flatMap(
                        membership ->
                                tenants.stream()
                                        .filter(t -> t.getId().equals(membership.getTenantId()))
                                        .map(
                                                tenant ->
                                                        new TenantOption(
                                                                tenant.getId(),
                                                                tenant.getName(),
                                                                tenant.getSlug(),
                                                                membership.getRole(),
                                                                tenant.getLogoUrl(),
                                                                toState(tenant.getStatus()))))
                .toList();
    }

    @Override
    public Optional<SessionSnapshot> sessionSnapshot(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            return Optional.empty();
        }
        return membershipRepository
                .findByTenantIdAndUserId(tenantId, userId)
                .flatMap(
                        membership ->
                                repository
                                        .findById(tenantId)
                                        .map(
                                                tenant ->
                                                        new SessionSnapshot(
                                                                toState(tenant.getStatus()),
                                                                toState(membership.getStatus()),
                                                                membership.getRoleChangedAt())));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TENANT_VIEW')")
    public TenantResponse currentDetail() {
        Tenant tenant = requireCurrent();
        return mapper.toResponse(tenant, settingsService.settingsOf(tenant.getId()));
    }

    /** Emissora do relatório (ver {@link TenantService#issuer()}). */
    @Override
    public com.devtime.tenant.dto.TenantViews.TenantIssuer issuer() {
        Tenant tenant = requireCurrent();
        return new com.devtime.tenant.dto.TenantViews.TenantIssuer(
                tenant.getId(),
                tenant.getName(),
                tenant.getLegalName(),
                tenant.getDocumentNumber(),
                tenant.getEmail(),
                tenant.getPhone(),
                tenant.getLogoUrl(),
                tenant.getAddress(),
                tenant.getTimezone(),
                tenant.getLocale(),
                tenant.getCurrency());
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TENANT_UPDATE')")
    public TenantResponse update(TenantUpdateRequest request) {
        Tenant tenant = requireCurrent();
        assertVersion(tenant, request.version()); // RN-004
        timezoneValidator.validate(request.timezone()); // INV-TEN-03

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        apply("name", request.name(), tenant.getName(), tenant::setName, before, after);
        apply(
                "legalName",
                request.legalName(),
                tenant.getLegalName(),
                tenant::setLegalName,
                before,
                after);
        apply(
                "documentNumber",
                request.documentNumber(),
                tenant.getDocumentNumber(),
                tenant::setDocumentNumber,
                before,
                after);
        apply("email", request.email(), tenant.getEmail(), tenant::setEmail, before, after);
        apply("phone", request.phone(), tenant.getPhone(), tenant::setPhone, before, after);
        // CX-07: alterar o fuso não recalcula nenhum workDate já persistido (CP-03, ART-005).
        apply(
                "timezone",
                request.timezone(),
                tenant.getTimezone(),
                tenant::setTimezone,
                before,
                after);
        apply("locale", request.locale(), tenant.getLocale(), tenant::setLocale, before, after);
        apply(
                "currency",
                request.currency(),
                tenant.getCurrency(),
                tenant::setCurrency,
                before,
                after);
        apply("logoUrl", request.logoUrl(), tenant.getLogoUrl(), tenant::setLogoUrl, before, after);
        if (request.address() != null) {
            before.put("address", String.valueOf(tenant.getAddress()));
            after.put("address", String.valueOf(request.address()));
            tenant.setAddress(request.address());
        }

        if (!after.isEmpty()) {
            auditService.record(ACTION_UPDATED, ENTITY_TYPE, tenant.getId(), before, after);
            log.info("organização alterada campos={}", after.keySet());
        }
        return mapper.toResponse(tenant, settingsService.settingsOf(tenant.getId()));
    }

    /**
     * §6.2: valida o <b>valor efetivo</b> e persiste apenas as chaves informadas.
     *
     * <p>CE-07: a auditoria é obrigatória aqui, e não opcional como em outras entidades — {@code
     * settings} decide cálculo de saldo e de cobrança, e uma alteração sem rastro tornaria
     * impossível explicar por que dois períodos com as mesmas horas produziram números diferentes.
     */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TENANT_UPDATE')")
    public TenantResponse updateSettings(TenantSettingsRequest request) {
        Tenant tenant = requireCurrent();
        assertVersion(tenant, request.version()); // RN-004

        TenantSettings current = settingsService.settingsOf(tenant.getId());
        TenantSettings effective = settingsWriter.merge(current, request);
        settingsValidator.validate(effective);

        Map<String, Object> changes = settingsWriter.changedKeys(current, effective);
        if (changes.isEmpty()) {
            return mapper.toResponse(tenant, current);
        }
        tenant.setSettings(settingsWriter.serialize(effective));
        auditService.record(
                ACTION_SETTINGS_UPDATED,
                ENTITY_TYPE,
                tenant.getId(),
                settingsWriter.previousValues(current, changes.keySet()),
                changes);
        events.publish(
                new TenantSettingsUpdatedEvent(tenant.getId(), List.copyOf(changes.keySet())));
        log.info("configurações do tenant alteradas chaves={}", changes.keySet());
        return mapper.toResponse(tenant, effective);
    }

    /** Ordem de §4.1 de state-machines.md e de §17.2: senha, confirmação, período em fechamento. */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TENANT_DELETE')")
    public TenantCancelResponse cancel(TenantCancelRequest request) {
        Tenant tenant = requireCurrent();
        UUID actorId = tenantContext.requireUserId();

        if (!userAccountService.matchesPassword(actorId, request.password())) {
            throw TenantExceptions.incorrectPassword(); // DEVTIME-1011
        }
        if (!TenantCancelRequest.EXPECTED_CONFIRMATION.equals(request.confirmation())) {
            throw TenantExceptions.settingOutOfRange(
                    "confirmation",
                    request.confirmation(),
                    TenantCancelRequest.EXPECTED_CONFIRMATION);
        }
        if (periodClosingStateSource.hasPeriodInClosing()) {
            throw TenantExceptions.cancellationBlockedByClosing(); // CX-12
        }

        Instant now = clock.instant();
        Instant purgeAt = now.plus(RETENTION);
        TenantStatus previous = tenant.getStatus();
        tenant.setStatus(TenantStatus.CANCELLED);
        tenant.setCancelledAt(now);
        tenant.setPurgeScheduledAt(purgeAt);
        tenant.setCancellationReason(request.reason());

        auditService.record(
                ACTION_CANCELLED,
                ENTITY_TYPE,
                tenant.getId(),
                Map.of("status", previous.name()),
                Map.of(
                        "status",
                        TenantStatus.CANCELLED.name(),
                        "purgeScheduledAt",
                        purgeAt.toString()));
        // A revogação das sessões vive em 001, que consome este evento: auth já depende de tenant,
        // e o caminho inverso fecharia um ciclo entre as features (BR-008).
        events.publish(new TenantCancelledEvent(tenant.getId(), actorId, purgeAt));
        log.error("organização cancelada purgeScheduledAt={}", purgeAt);
        return new TenantCancelResponse(TenantState.CANCELLED, purgeAt, purgeAt);
    }

    /**
     * RN-008: purga as organizações cuja retenção venceu.
     *
     * <p>A purga é <b>exclusão lógica</b>, não {@code DELETE} físico: P-03 e ART-051 proíbem
     * remover fisicamente entidade de domínio, e a obrigação legal de guarda do documento fiscal do
     * tenant é de cinco anos (§19.1), maior que a retenção de 30 dias. O que a exclusão lógica
     * garante é o exigido por RN-008: o dado deixa de ser alcançável por qualquer consulta.
     */
    @Override
    @Transactional
    public int purgeExpiredCancellations() {
        List<Tenant> due =
                repository.findPurgeDue(clock.instant(), PageRequest.of(0, PURGE_BATCH_SIZE));
        due.forEach(
                tenant -> {
                    // §19.1: a anonimização vem ANTES da exclusão da organização. Depois dela, os
                    // vínculos que respondem "esta pessoa participa de outra organização?" já
                    // teriam
                    // ido junto, e a purga deixaria dado pessoal para trás sem nada que o
                    // apontasse.
                    int anonymized =
                            userAccountService.anonymize(
                                    membershipRepository.findUserIdsOnlyIn(tenant.getId()));

                    repository.delete(
                            tenant); // Soft delete: SoftDeleteRepository preenche deletedAt.
                    auditService.recordSystemAction(
                            ACTION_PURGED,
                            ENTITY_TYPE,
                            tenant.getId(),
                            Map.of(
                                    "purgeScheduledAt",
                                    String.valueOf(tenant.getPurgeScheduledAt()),
                                    "anonymizedUsers",
                                    String.valueOf(anonymized)));
                });
        return due.size();
    }

    private Tenant requireCurrent() {
        UUID tenantId = tenantContext.requireTenantId(); // BR-042
        return repository
                .findById(tenantId)
                .orElseThrow(() -> EntityNotFoundException.of(Tenant.class, tenantId));
    }

    private void assertVersion(Tenant tenant, Long expected) {
        long current = tenant.getVersion() == null ? 0L : tenant.getVersion();
        if (expected == null || expected != current) {
            throw BusinessRuleException.versionConflict(ENTITY_TYPE, current);
        }
    }

    private void apply(
            String field,
            String candidate,
            String currentValue,
            java.util.function.Consumer<String> setter,
            Map<String, Object> before,
            Map<String, Object> after) {
        if (candidate == null || candidate.equals(currentValue)) {
            return;
        }
        before.put(field, currentValue);
        after.put(field, candidate);
        setter.accept(candidate);
    }

    private TenantView toView(Tenant tenant) {
        return new TenantView(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getTimezone(),
                tenant.getLocale(),
                tenant.getCurrency(),
                tenant.getLogoUrl(),
                toState(tenant.getStatus()),
                tenant.getPlanCode(),
                tenant.getSettings());
    }

    private TenantState toState(TenantStatus status) {
        return TenantState.valueOf(status.name());
    }

    private MembershipState toState(MembershipStatus status) {
        return MembershipState.valueOf(status.name());
    }
}
