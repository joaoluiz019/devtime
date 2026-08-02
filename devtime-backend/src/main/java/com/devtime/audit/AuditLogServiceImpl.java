package com.devtime.audit;

import com.devtime.audit.domain.AuditExceptions;
import com.devtime.audit.domain.AuditLog;
import com.devtime.audit.dto.AuditLogRequests.AuditLogFilter;
import com.devtime.audit.dto.AuditLogResponses.AuditLogResponse;
import com.devtime.shared.pagination.PageRequestFactory;
import com.devtime.shared.pagination.PageResponse;
import com.devtime.shared.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta da trilha (spec 002 §22.2).
 *
 * <p>Duas decisões de §20.1 governam esta classe e não são otimizações: o intervalo é <b>sempre</b>
 * aplicado, com 30 dias como padrão (CA-12), e nunca excede 90 dias (users.md §10.1). {@code
 * audit_logs} é particionada por mês, e uma consulta sem recorte percorreria todas as partições — a
 * operação mais cara do sistema, disponível a qualquer OWNER com um clique.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    /** CA-12: recorte aplicado quando a requisição não informa nenhum. */
    static final Duration DEFAULT_RANGE = Duration.ofDays(30);

    /** users.md §10.1: teto do intervalo consultável em uma requisição. */
    static final Duration MAX_RANGE = Duration.ofDays(90);

    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;
    private final AuditActorNameResolver actorNameResolver;
    private final TenantContext tenantContext;
    private final PageRequestFactory pageRequestFactory;
    private final java.time.Clock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'TENANT_AUDIT_VIEW')")
    public PageResponse<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable) {
        AuditLogFilter effective = filter == null ? AuditLogFilter.empty() : filter;
        Instant to = effective.occurredTo() == null ? clock.instant() : effective.occurredTo();
        Instant from =
                effective.occurredFrom() == null
                        ? to.minus(DEFAULT_RANGE)
                        : effective.occurredFrom();
        assertRangeWithinLimit(from, to);

        UUID tenantId = tenantContext.requireTenantId(); // BR-042
        Page<AuditLog> page =
                repository.findAll(
                        AuditLogSpecifications.of(tenantId, effective, from, to),
                        withDefaultSort(pageRequestFactory.validate(pageable)));

        Map<UUID, String> actors = resolveActors(page);
        log.info(
                "Consulta de auditoria: entityType={} action={} rangeDays={}",
                effective.entityType(),
                effective.action(),
                Duration.between(from, to).toDays());
        return PageResponse.of(page, entry -> mapper.toResponse(entry, actors));
    }

    /** §19.1: uma consulta, não uma por linha — a listagem repete os mesmos poucos autores. */
    private Map<UUID, String> resolveActors(Page<AuditLog> page) {
        Set<UUID> actorIds =
                page.getContent().stream()
                        .map(AuditLog::getActorId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return actorNameResolver.namesOf(actorIds);
    }

    private void assertRangeWithinLimit(Instant from, Instant to) {
        Duration range = Duration.between(from, to);
        if (range.compareTo(MAX_RANGE) > 0) {
            throw AuditExceptions.rangeTooWide(range.toDays(), MAX_RANGE.toDays()); // DEVTIME-3001
        }
    }

    /**
     * Ordenação padrão do mais recente para o mais antigo.
     *
     * <p>Casa com {@code idx_audit_logs_*}, todos declarados com {@code occurred_at DESC}: uma
     * ordenação diferente forçaria varredura e ordenação em memória.
     */
    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
    }
}
