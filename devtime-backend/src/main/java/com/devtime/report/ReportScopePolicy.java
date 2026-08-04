package com.devtime.report;

import com.devtime.report.domain.ReportExceptions;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Escopo de dados do relatório por papel (RN-711, CE-P-10, §16).
 *
 * <p><b>É a restrição mais dura do sistema para um papel</b> (OB-04). Um {@code MEMBER} vê todos os
 * tickets em tela, mas exporta apenas os próprios registros. A assimetria é deliberada: tela é
 * acesso controlado e auditável; arquivo é dado que sai do sistema e circula sem controle.
 *
 * <p><b>O escopo é verificado antes da existência do recurso</b> (§6.2, passos 2 e 3). Confirmar
 * primeiro que o contrato existe e só então recusar por permissão vazaria, pelo código de erro, que
 * o contrato existe — e a enumeração de contratos alheios seria uma sequência de {@code 403} contra
 * {@code 404}. Por isso esta policy roda sobre os <b>filtros</b>, sem tocar em nenhuma outra
 * feature.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScopePolicy {

    private final TenantContext tenantContext;

    /**
     * Resolve o escopo do solicitante e recusa filtros que o ampliariam.
     *
     * @return identificador ao qual os registros ficam restritos, ou vazio quando o papel enxerga o
     *     tenant inteiro. É o {@code restrictToUserId} que {@code WorkLogService.findForReport}
     *     recebe — o escopo entra na <b>consulta</b>, nunca em um filtro aplicado depois (SG-02)
     */
    public Optional<UUID> resolve(ReportType reportType, ReportFilters filters) {
        if (hasAnyScope()) {
            return Optional.empty();
        }

        // §16 e CX-21: os dois relatórios consolidados não existem para quem não vê o tenant.
        // Recusados pelo tipo, antes de qualquer filtro — não há recorte de `myWorkLogs` que
        // torne um resumo por cliente ou um relatório de produtividade legítimo para MEMBER.
        if (reportType == ReportType.CLIENT_SUMMARY || reportType == ReportType.PRODUCTIVITY) {
            throw violation(reportType.name());
        }

        UUID currentUserId = tenantContext.requireUserId();
        assertNoForeignUserFilter(filters, currentUserId);
        return Optional.of(currentUserId);
    }

    /**
     * CE-P-10 aplicada à exportação, que é mais restritiva que a consulta.
     *
     * <p>FA-17 e CP-09: {@code MEMBER} exporta <b>apenas</b> com escopo {@code myWorkLogs}.
     * Qualquer outro filtro — por cliente, por contrato, por usuário — retorna {@code 403}, mesmo
     * que ele tenha vínculo com o contrato. A razão está em §19.1: a exportação produz um arquivo
     * que sai do sistema, e um consolidado do tenant nas mãos de qualquer membro é um vazamento que
     * o controle de tela não impede.
     *
     * <p>A consulta em tela permanece permitida com os mesmos filtros (CE-R-10): lá o resultado já
     * chega restrito aos registros dele, e restringir também o recorte apenas esconderia informação
     * que ele pode ver.
     */
    public Optional<UUID> resolveForExport(ReportType reportType, ReportFilters filters) {
        Optional<UUID> restriction = resolve(reportType, filters);
        if (restriction.isEmpty()) {
            return restriction;
        }

        assertEmpty(filters.clientIds(), "clientIds");
        assertEmpty(filters.contractIds(), "contractIds");
        return restriction;
    }

    /** §16: papéis com {@code REPORT_VIEW_ANY} enxergam o tenant inteiro. */
    private boolean hasAnyScope() {
        return tenantContext.currentPermissions().contains(Permission.REPORT_VIEW_ANY);
    }

    /**
     * Filtrar por outro usuário é pedir dado de terceiro, com outro nome.
     *
     * <p>Pedir explicitamente o próprio identificador é permitido: é redundante, mas é o que a tela
     * envia quando o seletor de usuário vem preenchido, e recusá-lo transformaria uma interface
     * correta em erro.
     */
    private void assertNoForeignUserFilter(ReportFilters filters, UUID currentUserId) {
        List<UUID> userIds = filters == null ? null : filters.userIds();
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        if (userIds.size() > 1 || !userIds.contains(currentUserId)) {
            throw violation("userIds");
        }
    }

    private void assertEmpty(List<UUID> values, String filterName) {
        if (values != null && !values.isEmpty()) {
            throw violation(filterName);
        }
    }

    /**
     * §28: escopo violado é {@code WARN}.
     *
     * <p>Um {@code MEMBER} tentando exportar o consolidado do tenant é uma tentativa de acesso além
     * do permitido, e a recorrência é sinal a investigar. O log nomeia o filtro recusado e o
     * usuário — nunca o recurso, pelo mesmo motivo que a resposta não o nomeia.
     */
    private RuntimeException violation(String requestedFilter) {
        log.warn(
                "escopo de relatório violado userId={} filtro={}",
                tenantContext.currentUserId().orElse(null),
                requestedFilter);
        return ReportExceptions.scopeViolation(requestedFilter);
    }
}
