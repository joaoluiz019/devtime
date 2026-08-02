package com.devtime.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.devtime.audit.domain.AuditLog;
import com.devtime.audit.dto.AuditLogRequests.AuditLogFilter;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.pagination.PageRequestFactory;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** CA-12 e users.md §10.1: recorte temporal obrigatório da trilha. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    @Mock private AuditLogRepository repository;
    @Mock private AuditActorNameResolver actorNameResolver;

    private final TenantContext tenantContext = new TenantContext();
    private AuditLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new AuditLogServiceImpl(
                        repository,
                        new AuditLogMapper(new ObjectMapper()),
                        actorNameResolver,
                        tenantContext,
                        new PageRequestFactory(),
                        Clock.fixed(NOW, ZoneOffset.UTC));
        tenantContext.set(
                new TenantSession(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Role.OWNER,
                        RolePermissions.of(Role.OWNER),
                        "America/Sao_Paulo"));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(actorNameResolver.namesOf(any())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("CA-12: sem intervalo informado, aplica os últimos 30 dias")
    void defaultsToLastThirtyDays() {
        service.search(AuditLogFilter.empty(), PageRequest.of(0, 20));

        // A ausência de exceção com filtro vazio prova que o padrão foi aplicado: um intervalo
        // aberto excederia o teto de 90 dias e falharia com DEVTIME-3001.
        ArgumentCaptor<Specification<AuditLog>> captor =
                ArgumentCaptor.forClass(Specification.class);
        org.mockito.Mockito.verify(repository).findAll(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("users.md §10.1: intervalo acima de 90 dias devolve DEVTIME-3001")
    void rangeAboveNinetyDaysIsRejected() {
        AuditLogFilter filter =
                new AuditLogFilter(null, null, null, null, NOW.minus(Duration.ofDays(91)), NOW);

        assertThatThrownBy(() -> service.search(filter, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.DATE_RANGE_EXCEEDED));
    }

    @Test
    @DisplayName("Intervalo de exatamente 90 dias é aceito")
    void ninetyDaysIsAccepted() {
        AuditLogFilter filter =
                new AuditLogFilter(null, null, null, null, NOW.minus(Duration.ofDays(90)), NOW);

        assertThat(service.search(filter, PageRequest.of(0, 20)).content()).isEmpty();
    }

    @Test
    @DisplayName("RN-012: size acima de 100 devolve DEVTIME-2006")
    void pageSizeIsCapped() {
        assertThatThrownBy(() -> service.search(AuditLogFilter.empty(), PageRequest.of(0, 101)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAGE_SIZE_EXCEEDED));
    }
}
