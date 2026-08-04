package com.devtime.contract.dto;

import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.OveragePolicy;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.domain.RolloverPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs de saída da feature 004 (contracts.md §5, §7, §8 e §12.2). */
public final class ContractResponses {

    private ContractResponses() {}

    /** Cliente achatado na resposta do contrato (contracts.md §7). */
    @Schema(name = "ContractClientResponse")
    public record ContractClientResponse(UUID id, String name, String color) {}

    /**
     * Período do contrato.
     *
     * <p>Os campos de saldo ({@code availableMinutes}, {@code remainingMinutes}, {@code
     * consumptionRate}) pertencem a {@code 011-bank-hours} e não são calculados aqui: a fronteira
     * de §4 da spec é explícita — {@code 004} nunca calcula saldo.
     */
    @Schema(name = "ContractPeriodResponse")
    public record ContractPeriodResponse(
            UUID id,
            int sequence,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            PeriodStatus status,
            int contractedMinutes,
            int carriedInMinutes,
            int adjustmentMinutes,
            int consumedMinutes,
            int nonBillableMinutes,
            String currency) {}

    /**
     * Referência enxuta do contrato para features consumidoras (AR-02).
     *
     * <p>{@code status} é {@code String} e não o enum: {@code ContractStatus} pertence ao domínio
     * de {@code 004}, e AR-02 proíbe que {@code 007} ou {@code 008} dependam dele. A decisão de
     * negócio que essas features realmente precisam — se o contrato aceita registro de horas
     * (RN-306) — chega calculada em {@code acceptsWorkLogs}, tomada por quem é dono da regra.
     *
     * @param acceptsWorkLogs RN-306: verdadeiro apenas em {@code ACTIVE} e {@code SUSPENDED}
     */
    @Schema(name = "ContractRefResponse")
    public record ContractRefResponse(
            UUID id,
            String code,
            String name,
            String status,
            boolean acceptsWorkLogs,
            ContractClientResponse client) {}

    /**
     * Contrato como {@code 008-worklogs} precisa dele (AR-02).
     *
     * <p>Distinta de {@link ContractRefResponse} porque o registro de horas depende de três coisas
     * que a referência enxuta não carrega: a vigência (RN-117), a política de excedente (RN-231) e
     * o cliente a copiar para o work log (RN-109). Distinta de {@link ContractResponse} porque
     * aquela expõe {@code ContractType}, {@code ContractStatus} e {@code OveragePolicy} — enums do
     * domínio de {@code 004} que AR-02 proíbe {@code 008} de conhecer.
     *
     * @param acceptsWorkLogs RN-306, já decidido por quem é dono da regra
     * @param overagePolicy nome do valor de {@code OveragePolicy}; a decisão de bloquear, avisar ou
     *     permitir é de {@code 008}, mas o valor pertence ao contrato
     */
    @Schema(name = "ContractWorkLogRefResponse")
    public record ContractWorkLogRefResponse(
            UUID id,
            String code,
            String name,
            UUID clientId,
            String status,
            boolean acceptsWorkLogs,
            LocalDate startDate,
            LocalDate endDate,
            String overagePolicy,
            UUID defaultCategoryId,
            String currency) {}

    /**
     * Período como as features consumidoras precisam dele (AR-02).
     *
     * <p>Mesmo motivo de {@link ContractWorkLogRefResponse}: {@link ContractPeriodResponse} expõe
     * {@code PeriodStatus}, e {@code 008} não pode depender do domínio de {@code 004}. {@code
     * acceptsWorkLogs} traz a decisão já tomada — {@code OPEN} e {@code REOPENED} aceitam escrita
     * (§4.6 de state-machines.md, CX-24) —, evitando que a feature consumidora reimplemente a
     * leitura da máquina de estados.
     */
    @Schema(name = "ContractPeriodRefResponse")
    public record ContractPeriodRefResponse(
            UUID id,
            UUID contractId,
            int sequence,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            boolean acceptsWorkLogs,
            int contractedMinutes,
            int carriedInMinutes,
            int adjustmentMinutes,
            int consumedMinutes,
            int nonBillableMinutes,
            String currency) {}

    /**
     * Contrato próximo do fim, para o lembrete de RN-606.
     *
     * <p>Carrega o {@code tenantId} porque o job que a consome percorre <b>todos</b> os tenants e
     * precisa definir o contexto a cada iteração (BR-049) — sem ele, a resolução de destinatários
     * usaria a organização errada.
     */
    @Schema(name = "ContractReminderView")
    public record ContractReminderView(
            UUID tenantId, UUID contractId, String code, String name, LocalDate endDate) {}

    /** Período próximo do fechamento, para o lembrete de RN-605. Mesma razão para o tenant. */
    @Schema(name = "PeriodReminderView")
    public record PeriodReminderView(
            UUID tenantId, UUID periodId, UUID contractId, String label, LocalDate endDate) {}

    /**
     * Alvo de um job de manutenção de {@code 004} (§22.4).
     *
     * <p>Carrega o {@code tenantId} pelo mesmo motivo de {@link ContractReminderView}: a varredura
     * é de plataforma e o contexto precisa ser definido a cada iteração (BR-049, JB-06). Carrega
     * apenas identificadores — o job não decide nada com base no conteúdo, apenas delega ao serviço
     * tenant-scoped, que recarrega a entidade sob o contexto correto.
     */
    @Schema(name = "MaintenanceTarget")
    public record MaintenanceTarget(UUID tenantId, UUID entityId, UUID contractId) {}

    /**
     * Cartão de contrato do painel (specs/010 §23, {@code ContractStatusDto}).
     *
     * <p>Interface pública para {@code 010-dashboard}. Carrega o que o painel precisa e que a
     * listagem de contratos não oferece: o período <b>corrente</b> já resolvido e os {@code
     * notificationThresholds}, sem os quais o painel derivaria severidade por 50/80/100 fixos — o
     * que CP-04 daquela spec proíbe justamente porque divergiria do alerta por e-mail do mesmo
     * contrato (RN-602).
     *
     * <p>Nenhum campo monetário: {@code ContractStatusDto} de §23 e o exemplo de reports.md §10.1
     * não expõem valor algum, o que satisfaz INV-DSH-04 por construção em vez de por omissão
     * condicional.
     *
     * <p>{@code periodId} é nulo apenas em contrato sem nenhum período materializado — situação
     * transitória entre a criação e a ativação (RN-209).
     */
    @Schema(name = "ContractDashboardCard")
    public record ContractDashboardCard(
            UUID contractId,
            String code,
            String name,
            UUID clientId,
            UUID periodId,
            String periodLabel,
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            LocalDate contractEndDate,
            List<Integer> notificationThresholds) {}

    /**
     * Contrato como {@code 012-reports} precisa dele (AR-02, ART-065).
     *
     * <p>{@link ContractResponse} expõe {@code ContractType}, {@code ContractStatus}, {@code
     * RolloverPolicy} e {@code OveragePolicy} — enums do domínio de {@code 004} que a feature
     * consumidora não pode conhecer. Aqui o tipo chega como texto, que é o que o cabeçalho do
     * relatório imprime.
     *
     * <p>{@code hourlyRate} e {@code overageRate} continuam sujeitos a {@code
     * CONTRACT_VIEW_FINANCIAL} (SG-03): a omissão acontece nesta feature, que é a dona da regra, e
     * não em quem consome — o relatório recebe nulo e omite as colunas sem saber por quê (CP-03).
     */
    @Schema(name = "ContractReportRef")
    public record ContractReportRef(
            UUID id,
            String code,
            String name,
            String type,
            Integer monthlyMinutes,
            BigDecimal hourlyRate,
            BigDecimal overageRate,
            String currency,
            UUID clientId) {}

    /**
     * Período como {@code 012-reports} precisa dele (AR-02, RN-701).
     *
     * <p>{@link ContractPeriodResponse} expõe {@code PeriodStatus}, e a decisão que o relatório
     * realmente precisa tomar — servir do snapshot ou calcular ao vivo (§6.1 de specs/012) — chega
     * aqui <b>calculada</b>, em {@code isClosed} e {@code isStarted}. É a mesma inversão de {@code
     * ContractRefResponse.acceptsWorkLogs}: quem é dono da máquina de estados decide o que ela
     * significa, e o consumidor não replica a tabela de status.
     */
    @Schema(name = "PeriodReportRef")
    public record PeriodReportRef(
            UUID id,
            UUID contractId,
            int sequence,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            boolean isClosed,
            boolean isStarted,
            int reopenCount,
            String currency) {}

    /** Item de {@code periodsPreview} (contracts.md §5 e §6). */
    @Schema(name = "PeriodPreviewItem")
    public record PeriodPreviewItem(
            int sequence,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            int contractedMinutes,
            boolean prorated,
            String prorationBasis) {}

    @Schema(name = "PeriodPreviewResponse")
    public record PeriodPreviewResponse(List<PeriodPreviewItem> periodsPreview) {}

    /**
     * Detalhe do contrato.
     *
     * <p>SG-03: os campos monetários são nulos quando o requisitante não possui {@code
     * CONTRACT_VIEW_FINANCIAL} — a omissão ocorre no backend, nunca apenas na interface.
     */
    @Schema(name = "ContractResponse")
    public record ContractResponse(
            UUID id,
            String code,
            String name,
            String description,
            ContractClientResponse client,
            ContractType type,
            ContractStatus status,
            Integer monthlyMinutes,
            LocalDate startDate,
            LocalDate endDate,
            int billingDay,
            RolloverPolicy rolloverPolicy,
            Integer rolloverCapMinutes,
            int rolloverExpiryPeriods,
            OveragePolicy overagePolicy,
            BigDecimal hourlyRate,
            BigDecimal overageRate,
            String currency,
            boolean autoRenew,
            boolean prorateFirstPeriod,
            List<Integer> notificationThresholds,
            UUID defaultCategoryId,
            String notes,
            ContractPeriodResponse currentPeriod,
            List<PeriodPreviewItem> periodsPreview,
            long version,
            List<String> availableTransitions,
            List<String> availableActions) {}

    /** Item da listagem (contracts.md §7) — projeção, nunca a entidade (BR-107). */
    @Schema(name = "ContractListItemResponse")
    public record ContractListItemResponse(
            UUID id,
            String code,
            String name,
            ContractClientResponse client,
            ContractType type,
            ContractStatus status,
            Integer monthlyMinutes,
            LocalDate startDate,
            LocalDate endDate,
            ContractPeriodResponse currentPeriod,
            long version) {}

    /** contracts.md §8.1. */
    @Schema(name = "ContractActivationResponse")
    public record ContractActivationResponse(
            ContractStatus status, ContractPeriodResponse firstPeriod) {}

    /** contracts.md §8.3: períodos gerados para preservar a contiguidade após a suspensão. */
    @Schema(name = "ContractTransitionResponse")
    public record ContractTransitionResponse(
            ContractStatus status,
            List<ContractPeriodResponse> generatedPeriods,
            ContractPeriodResponse truncatedPeriod) {}

    /**
     * Série histórica dos períodos (contracts.md §12.2).
     *
     * <p>Os agregados de consumo e a tendência dependem de horas registradas, que chegam com {@code
     * 008}; os campos existem e refletem os valores correntes dos períodos.
     */
    @Schema(name = "ContractHistoryResponse")
    public record ContractHistoryResponse(
            UUID contractId,
            List<ContractHistoryPeriod> periods,
            ContractHistoryAggregates aggregates) {}

    @Schema(name = "ContractHistoryPeriod")
    public record ContractHistoryPeriod(
            int sequence,
            String label,
            PeriodStatus status,
            int contractedMinutes,
            int carriedInMinutes,
            int adjustmentMinutes,
            int consumedMinutes,
            int remainingMinutes,
            int overageMinutes,
            int carriedOutMinutes) {}

    @Schema(name = "ContractHistoryAggregates")
    public record ContractHistoryAggregates(
            int periodsCount,
            int periodsWithOverage,
            int totalOverageMinutes,
            int totalCarriedOutMinutes) {}

    /** clients.md §8: resumo consolidado do cliente, servido por esta feature (ver §22.1). */
    @Schema(name = "ClientContractSummaryResponse")
    public record ClientContractSummaryResponse(
            UUID clientId,
            String currency,
            SummaryTotals totals,
            List<ContractHistoryPeriod> history,
            List<ContractSummaryByContract> byContract) {}

    @Schema(name = "SummaryTotals")
    public record SummaryTotals(
            int contractedMinutes,
            int consumedMinutes,
            int nonBillableMinutes,
            int remainingMinutes,
            int overageMinutes) {}

    @Schema(name = "ContractSummaryByContract")
    public record ContractSummaryByContract(
            UUID contractId, String code, String name, int minutes) {}
}
