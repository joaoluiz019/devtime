package com.devtime.report;

import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogReportViews.ReportEntryFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ponte única entre os relatórios e as linhas de registro de horas (RN-704, passo 7 de §6.2).
 *
 * <p><b>Todo caminho de leitura passa por aqui</b>, e é assim que RN-704 deixa de ser uma
 * lembrança: registros logicamente excluídos não aparecem porque {@code
 * WorkLogService.findForReport} os remove por construção, não porque cada um dos cinco relatórios
 * lembrou de filtrar. A ordenação normativa de §6.3 vem pelo mesmo caminho.
 *
 * <p>CP-19 e AR-02: a feature nunca toca o repositório de {@code 008}. O que ela conhece é a
 * interface pública da feature dona da tabela.
 */
@Component
@RequiredArgsConstructor
public class ReportEntryLoader {

    private final WorkLogService workLogService;
    private final LiveReportMapper liveReportMapper;
    private final TenantContext tenantContext;

    /** Linhas já formatadas, na ordem normativa. */
    public List<ReportEntry> load(ReportEntryFilter filter, BigDecimal hourlyRate) {
        return liveReportMapper.entries(workLogService.findForReport(filter), hourlyRate);
    }

    /**
     * As mesmas linhas, ainda na projeção de {@code 008}.
     *
     * <p>Existe para o relatório de período ao vivo, cujo mapeamento é feito por {@link
     * LiveReportMapper} junto com saldo, ajustes e contrato — mapear aqui e remapear lá aplicaria a
     * formatação duas vezes.
     */
    public List<com.devtime.worklog.dto.WorkLogReportViews.ReportEntry> rawEntries(
            ReportEntryFilter filter) {
        return workLogService.findForReport(filter);
    }

    /**
     * Quantas linhas o mesmo recorte produz (passo 10 de §6.2).
     *
     * <p>Existe separado de {@link #load} porque RN-706 decide entre exportação síncrona e
     * assíncrona <b>antes</b> de materializar o resultado: contar carregando as linhas anularia a
     * proteção exatamente no passo que existe para evitá-la (CP-13).
     */
    public long count(ReportEntryFilter filter) {
        return workLogService.countForReport(filter);
    }

    /**
     * Compõe o recorte a partir dos filtros do pedido e do escopo já resolvido.
     *
     * <p>ART-021: nenhum campo vem do cliente sem passar por aqui, e {@code restrictToUserId}
     * <b>nunca</b> vem do cliente — é o que a {@code ReportScopePolicy} decidiu.
     */
    public ReportEntryFilter filterFor(
            UUID contractId,
            UUID contractPeriodId,
            UUID clientId,
            UUID ticketId,
            LocalDate from,
            LocalDate to,
            ReportFilters filters,
            UUID restrictToUserId) {
        return new ReportEntryFilter(
                contractId,
                contractPeriodId,
                clientId,
                ticketId,
                from,
                to,
                filters.userIds(),
                filters.categoryIds(),
                filters.tagIds(),
                filters.billableFilter(),
                restrictToUserId);
    }

    /**
     * CP-03 e SG-07: o pedido pode <b>pedir</b> os valores; só a permissão os concede.
     *
     * <p>A verificação fica aqui, e não no controller, porque vale igualmente para a exportação — e
     * é no arquivo que a omissão importa mais, já que ele sai do sistema e circula sem controle
     * (CP-08).
     */
    public boolean includeFinancial(ReportFilters filters) {
        return filters.includeFinancialOrDefault()
                && tenantContext.currentPermissions().contains(Permission.CONTRACT_VIEW_FINANCIAL);
    }

    /** CP-04: a distribuição por usuário não existe para quem só enxerga os próprios registros. */
    public boolean includeByUser() {
        return tenantContext.currentPermissions().contains(Permission.REPORT_VIEW_ANY);
    }
}
