package com.devtime.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.dashboard.domain.DashboardExceptions;
import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.shared.time.DateRange;
import com.devtime.shared.time.TenantClock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Resolução do intervalo consultado (§22.3 de specs/010, RN-009, RN-705).
 *
 * <p>O relógio é fixo em <b>29/07/2026 às 02:00 UTC</b>, que em {@code America/Sao_Paulo} ainda é
 * <b>28/07</b>. É a escolha que torna CX-19 verificável: se a resolução usasse UTC, "hoje" sairia
 * um dia à frente para o tenant.
 */
class DashboardPeriodResolverTest {

    private static final Instant FIXED = Instant.parse("2026-07-29T02:00:00Z");
    private static final LocalDate TENANT_TODAY = LocalDate.of(2026, 7, 28);

    private final TenantContext tenantContext = new TenantContext();
    private final DashboardPeriodResolver resolver =
            new DashboardPeriodResolver(
                    new TenantClock(Clock.fixed(FIXED, ZoneOffset.UTC), tenantContext));

    private void sessionInZone(String timezone) {
        tenantContext.set(
                new TenantSession(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Role.OWNER,
                        RolePermissions.of(Role.OWNER),
                        timezone));
    }

    @AfterEach
    void clear() {
        tenantContext.clear();
    }

    @Test
    @DisplayName("CX-19 / CA-11: a resolução usa o fuso do TENANT, não UTC")
    void resolvesInTenantZone() {
        sessionInZone("America/Sao_Paulo");

        DateRange range = resolver.resolve(DashboardPeriodType.LAST_7_DAYS, null, null);

        assertThat(range.end())
                .as("02:00 UTC de 29/07 ainda é 28/07 em São Paulo")
                .isEqualTo(TENANT_TODAY);
    }

    @Test
    @DisplayName("CX-19: o mesmo instante produz outro dia em um tenant a leste de Greenwich")
    void differentTenantZoneProducesDifferentToday() {
        sessionInZone("Asia/Tokyo");

        DateRange range = resolver.resolve(DashboardPeriodType.LAST_7_DAYS, null, null);

        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 7, 29));
    }

    @Test
    @DisplayName("§10.1: CURRENT_PERIOD é o mês corrente do calendário, no fuso do tenant")
    void currentPeriodIsCalendarMonth() {
        sessionInZone("America/Sao_Paulo");

        DateRange range = resolver.resolve(DashboardPeriodType.CURRENT_PERIOD, null, null);

        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("§17.1: período nulo aplica CURRENT_PERIOD em vez de falhar")
    void nullPeriodDefaultsToCurrent() {
        sessionInZone("America/Sao_Paulo");

        assertThat(resolver.resolve(null, null, null))
                .isEqualTo(resolver.resolve(DashboardPeriodType.CURRENT_PERIOD, null, null));
    }

    @Test
    @DisplayName("BR-149: LAST_7_DAYS é intervalo fechado e inclui hoje")
    void lastSevenDaysIsClosedAndIncludesToday() {
        sessionInZone("America/Sao_Paulo");

        DateRange range = resolver.resolve(DashboardPeriodType.LAST_7_DAYS, null, null);

        assertThat(range.lengthInDays()).isEqualTo(7);
        assertThat(range.start()).isEqualTo(TENANT_TODAY.minusDays(6));
    }

    @Test
    @DisplayName("BR-149: LAST_30_DAYS cobre exatamente 30 dias")
    void lastThirtyDays() {
        sessionInZone("America/Sao_Paulo");

        assertThat(resolver.resolve(DashboardPeriodType.LAST_30_DAYS, null, null).lengthInDays())
                .isEqualTo(30);
    }

    @Test
    @DisplayName("CX-18 / CA-13: intervalo personalizado de 366 dias é aceito")
    void customRangeOf366DaysIsAccepted() {
        sessionInZone("America/Sao_Paulo");
        LocalDate from = LocalDate.of(2026, 1, 1);

        DateRange range = resolver.resolve(DashboardPeriodType.CUSTOM, from, from.plusDays(365));

        assertThat(range.lengthInDays()).isEqualTo(366);
    }

    @Test
    @DisplayName("RN-705 / CA-13: 367 dias é rejeitado com DEVTIME-3001")
    void customRangeOf367DaysIsRejected() {
        sessionInZone("America/Sao_Paulo");
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        DashboardPeriodType.CUSTOM, from, from.plusDays(366)))
                .isInstanceOf(DashboardExceptions.DateRangeTooLargeException.class)
                .extracting(exception -> ((BusinessRuleException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DATE_RANGE_EXCEEDED);
    }

    @Test
    @DisplayName("§17.1: CUSTOM sem from ou to é rejeitado como erro de formato")
    void customRequiresBothBounds() {
        sessionInZone("America/Sao_Paulo");

        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        DashboardPeriodType.CUSTOM, LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(DashboardExceptions.DashboardValidationException.class);
    }

    @Test
    @DisplayName("§17.1: to anterior a from é rejeitado antes de qualquer agregação")
    void invertedRangeIsRejected() {
        sessionInZone("America/Sao_Paulo");

        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        DashboardPeriodType.CUSTOM,
                                        LocalDate.of(2026, 7, 10),
                                        LocalDate.of(2026, 7, 1)))
                .isInstanceOf(DashboardExceptions.DashboardValidationException.class);
    }

    @Test
    @DisplayName("SG-07: a chave de cache deriva das datas resolvidas, não do tipo pedido")
    void cacheKeyDerivesFromResolvedDates() {
        sessionInZone("America/Sao_Paulo");

        DateRange range = resolver.resolve(DashboardPeriodType.LAST_7_DAYS, null, null);

        assertThat(resolver.cacheKeyOf(range)).isEqualTo(range.start() + ":" + range.end());
    }
}
