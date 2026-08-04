package com.devtime.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * T-012-32 — escopo por papel (RN-711, CE-P-10, INV-RPT-04, R-05).
 *
 * <p>É a restrição mais dura do sistema para um papel (OB-04) e a que mais depende de teste por
 * <b>combinação</b>: cada filtro que o {@code MEMBER} pudesse informar é uma porta separada, e
 * basta uma esquecida para que o consolidado do tenant saia em um arquivo.
 */
@ExtendWith(MockitoExtension.class)
class ReportScopePolicyTest {

    private static final UUID CURRENT_USER =
            UUID.fromString("0192f3a4-0000-7000-8000-0000000000a1");
    private static final UUID OTHER_USER = UUID.fromString("0192f3a4-0000-7000-8000-0000000000b2");
    private static final UUID SOME_ID = UUID.fromString("0192f3a4-0000-7000-8000-0000000000c3");

    @Mock private TenantContext tenantContext;

    @InjectMocks private ReportScopePolicy policy;

    @BeforeEach
    void currentUser() {
        lenient().when(tenantContext.requireUserId()).thenReturn(CURRENT_USER);
        lenient().when(tenantContext.currentUserId()).thenReturn(Optional.of(CURRENT_USER));
    }

    private void asMember() {
        when(tenantContext.currentPermissions()).thenReturn(Set.of(Permission.REPORT_VIEW_OWN));
    }

    private void asManager() {
        when(tenantContext.currentPermissions())
                .thenReturn(Set.of(Permission.REPORT_VIEW_OWN, Permission.REPORT_VIEW_ANY));
    }

    private ReportFilters withUsers(List<UUID> userIds) {
        return new ReportFilters(
                null, null, null, null, null, null, null, null, null, null, userIds, null);
    }

    @Test
    @DisplayName("§16: papel com REPORT_VIEW_ANY enxerga o tenant inteiro, sem restrição")
    void anyScopeIsUnrestricted() {
        asManager();

        assertThat(policy.resolve(ReportType.CONTRACT_PERIOD, ReportFilters.empty())).isEmpty();
    }

    @Test
    @DisplayName("RN-711 / CE-R-10 / CX-22: MEMBER recebe o próprio escopo, não um erro")
    void memberIsRestrictedToOwnRecords() {
        asMember();

        assertThat(policy.resolve(ReportType.CONTRACT_PERIOD, ReportFilters.empty()))
                .contains(CURRENT_USER);
    }

    @Test
    @DisplayName("CX-21: MEMBER não acessa produtividade nem resumo por cliente")
    void consolidatedTypesAreDeniedToMember() {
        asMember();

        assertThatThrownBy(() -> policy.resolve(ReportType.PRODUCTIVITY, ReportFilters.empty()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).getErrorCode())
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("RN-711: filtrar por outro usuário é pedir dado de terceiro, com outro nome")
    void foreignUserFilterIsDenied() {
        asMember();

        assertThatThrownBy(
                        () -> policy.resolve(ReportType.TIMESHEET, withUsers(List.of(OTHER_USER))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Pedir explicitamente o próprio identificador é permitido — a tela envia assim")
    void ownUserFilterIsAllowed() {
        asMember();

        assertThat(policy.resolve(ReportType.TIMESHEET, withUsers(List.of(CURRENT_USER))))
                .contains(CURRENT_USER);
    }

    @Test
    @DisplayName("CE-P-10 / CP-09: na exportação, MEMBER com filtro por cliente recebe 403")
    void exportDeniesClientFilterForMember() {
        asMember();
        ReportFilters byClient =
                new ReportFilters(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(SOME_ID),
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> policy.resolveForExport(ReportType.TIMESHEET, byClient))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).getErrorCode())
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("CE-P-10 / CP-09: na exportação, MEMBER com filtro por contrato recebe 403")
    void exportDeniesContractFilterForMember() {
        asMember();
        ReportFilters byContract =
                new ReportFilters(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(SOME_ID),
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> policy.resolveForExport(ReportType.TIMESHEET, byContract))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("CE-R-10: os mesmos filtros continuam permitidos na consulta em tela")
    void screenQueryAllowsWhatExportDenies() {
        asMember();
        ReportFilters byContract =
                new ReportFilters(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(SOME_ID),
                        null,
                        null,
                        null,
                        null,
                        null);

        // A assimetria é deliberada (OB-04): tela é acesso controlado e auditável; arquivo é dado
        // que sai do sistema e circula sem controle.
        assertThat(policy.resolve(ReportType.TIMESHEET, byContract)).contains(CURRENT_USER);
    }

    @Test
    @DisplayName("CE-P-10: MEMBER com escopo myWorkLogs exporta normalmente")
    void exportAllowsMyWorkLogsScope() {
        asMember();

        assertThat(policy.resolveForExport(ReportType.TIMESHEET, ReportFilters.empty()))
                .contains(CURRENT_USER);
    }

    @Test
    @DisplayName("§16: quem enxerga o tenant exporta sem restrição de filtro")
    void exportIsUnrestrictedForAnyScope() {
        asManager();

        assertThat(policy.resolveForExport(ReportType.CLIENT_SUMMARY, ReportFilters.empty()))
                .isEmpty();
    }
}
