package com.devtime.tenant;

import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.domain.Tenant;
import com.devtime.tenant.domain.TenantStatus;
import com.devtime.tenant.dto.TenantCommands.NewTenant;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import com.devtime.tenant.dto.TenantViews.SessionSnapshot;
import com.devtime.tenant.dto.TenantViews.TenantOption;
import com.devtime.tenant.dto.TenantViews.TenantState;
import com.devtime.tenant.dto.TenantViews.TenantView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link TenantService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantServiceImpl implements TenantService {

    private static final String DEFAULT_LOCALE = "pt-BR";
    private static final String DEFAULT_CURRENCY = "BRL";
    private static final String DEFAULT_SETTINGS = "{}";
    private static final String DEFAULT_PLAN_CODE = "FREE"; // OB-04: ignorado no MVP

    private final TenantRepository repository;
    private final MembershipRepository membershipRepository;
    private final SlugGenerator slugGenerator;

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
