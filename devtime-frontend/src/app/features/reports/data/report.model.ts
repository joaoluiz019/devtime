/**
 * Modelos de relatório e exportação, espelhando os DTOs publicados no OpenAPI (FR-061, AP-02).
 *
 * Nenhum campo é renomeado, transformado ou derivado: o derivado vive em `computed` no store
 * (FR-042) e o formatado vive em pipes (FR-044). Os cinco tipos de relatório repetem os campos de
 * cabeçalho porque é assim que o backend os serializa — achatá-los num tipo comum aqui obrigaria a
 * desfazer o achatamento a cada leitura.
 */

/** Os cinco tipos de §4 de `reports.md`. Conjunto fechado por RS-08. */
export type ReportType =
  | 'CONTRACT_PERIOD'
  | 'CLIENT_SUMMARY'
  | 'TIMESHEET'
  | 'TICKET_DETAIL'
  | 'PRODUCTIVITY';

/** Os sete agrupamentos de §5.1, incluindo a lista plana. */
export type ReportGrouping = 'DATE' | 'WEEK' | 'TICKET' | 'CATEGORY' | 'USER' | 'TAG' | 'NONE';

/** §6.1: `SNAPSHOT` é documento definitivo; `LIVE` é número em evolução. */
export type ReportSource = 'SNAPSHOT' | 'LIVE';

export type ExportFormat = 'PDF' | 'XLSX' | 'CSV';

/** §4.10 de `state-machines.md`. */
export type ExportStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'EXPIRED';

/**
 * Agrupamentos aceitos por tipo (§6.3), espelhando `ReportGrouping.supportedBy` do backend.
 *
 * A tabela é replicada no cliente para que o seletor não ofereça uma combinação que o servidor
 * recusaria com `DEVTIME-3007` — oferecer e recusar depois é pior do que não oferecer. A fonte da
 * verdade continua sendo o backend: esta cópia só evita a viagem.
 */
export const GROUPINGS_BY_TYPE: Readonly<Record<ReportType, readonly ReportGrouping[]>> = {
  CONTRACT_PERIOD: ['DATE', 'TICKET', 'CATEGORY', 'USER', 'TAG', 'NONE'],
  CLIENT_SUMMARY: ['DATE', 'TICKET', 'CATEGORY', 'USER', 'TAG', 'NONE'],
  TIMESHEET: ['DATE', 'WEEK', 'TICKET', 'CATEGORY', 'USER', 'TAG', 'NONE'],
  TICKET_DETAIL: ['DATE', 'CATEGORY', 'NONE'],
  PRODUCTIVITY: ['DATE', 'WEEK', 'CATEGORY', 'USER', 'NONE'],
};

/** RN-705: nenhum relatório aceita intervalo maior que isto. Validado também no cliente. */
export const MAX_RANGE_DAYS = 366;

/** RN-706: acima deste número de linhas a exportação vira assíncrona. */
export const SYNC_EXPORT_ROW_LIMIT = 5000;

export interface ReportUserRef {
  readonly id: string;
  readonly name: string;
}

export interface ReportAddress {
  readonly street: string | null;
  readonly number: string | null;
  readonly complement: string | null;
  readonly district: string | null;
  readonly city: string | null;
  readonly state: string | null;
  readonly postalCode: string | null;
  readonly country: string | null;
}

export interface ReportIssuer {
  readonly name: string;
  readonly legalName: string | null;
  readonly documentNumber: string | null;
  readonly email: string | null;
  readonly phone: string | null;
  readonly logoUrl: string | null;
  readonly address: ReportAddress | null;
}

export interface ReportClient {
  readonly name: string;
  readonly legalName: string | null;
  readonly documentNumber: string | null;
  readonly address: ReportAddress | null;
}

export interface ReportContract {
  readonly code: string;
  readonly name: string;
  readonly type: string;
  readonly monthlyMinutes: number | null;
}

export interface ReportPeriod {
  readonly label: string;
  readonly sequence: number;
  readonly startDate: string;
  readonly endDate: string;
  readonly status: string;
}

export interface ReportRange {
  readonly from: string;
  readonly to: string;
}

export interface ReportBalance {
  readonly contractedMinutes: number;
  readonly carriedInMinutes: number;
  readonly adjustmentMinutes: number;
  readonly availableMinutes: number;
  readonly consumedMinutes: number;
  readonly nonBillableMinutes: number;
  readonly remainingMinutes: number;
  readonly overageMinutes: number;
  readonly carriedOutMinutes: number;
  readonly consumptionRate: number;
}

export interface ReportAdjustment {
  readonly minutes: number;
  readonly reason: string;
  readonly justification: string;
  readonly appliedBy: string;
  readonly appliedAt: string;
}

/** CP-03 / CP-08: vem nulo sem `CONTRACT_VIEW_FINANCIAL`. A omissão é do servidor. */
export interface ReportFinancial {
  readonly currency: string;
  readonly hourlyRate: number | null;
  readonly overageRate: number | null;
  readonly regularMinutes: number;
  readonly regularValue: number | null;
  readonly overageMinutes: number;
  readonly overageValue: number | null;
  readonly totalValue: number | null;
}

export interface ReportEntry {
  readonly workDate: string;
  readonly startedAt: string | null;
  readonly endedAt: string | null;
  readonly ticketKey: string | null;
  readonly ticketTitle: string | null;
  readonly categoryName: string | null;
  readonly userName: string | null;
  readonly description: string | null;
  readonly netMinutes: number;
  /** RN-710: `HH:MM` formatado pelo servidor, para que tela, PDF, XLSX e CSV digam o mesmo. */
  readonly durationLabel: string;
  readonly decimalHours: number;
  readonly billable: boolean;
  readonly tags: readonly string[];
  readonly value: number | null;
}

/** Com `groupBy=NONE` o servidor devolve um único grupo de `key` nula: é a lista plana de §5.1. */
export interface ReportGroup {
  readonly key: string | null;
  readonly label: string | null;
  readonly totalNetMinutes: number;
  readonly totalBillableMinutes: number;
  readonly durationLabel: string;
  readonly entries: readonly ReportEntry[];
}

export interface ReportSummarySlice {
  readonly key: string;
  readonly label: string;
  readonly color: string | null;
  readonly minutes: number;
  readonly percentage: number;
}

/** CP-04: `byUser` vem nulo para `MEMBER`. */
export interface ReportSummaries {
  readonly byCategory: readonly ReportSummarySlice[];
  readonly byTicket: readonly ReportSummarySlice[];
  readonly byUser: readonly ReportSummarySlice[] | null;
}

export interface ReportTotals {
  readonly entriesCount: number;
  readonly distinctDays: number;
  readonly distinctTickets: number;
  readonly netMinutes: number;
  readonly billableMinutes: number;
  readonly nonBillableMinutes: number;
  readonly durationLabel: string;
  readonly decimalHours: number;
  readonly totalValue: number | null;
}

/** Cabeçalho comum aos cinco tipos, na forma plana em que o servidor o serializa. */
export interface ReportHeader {
  readonly reportType: ReportType;
  readonly generatedAt: string;
  readonly generatedBy: ReportUserRef;
  /** RN-703 / PDF-07: identificador único da emissão, rastreável do arquivo até a exportação. */
  readonly issueId: string;
  readonly source: ReportSource;
  /** RP-02 / CP-02: obriga a marcação PARCIAL na tela, no PDF e no Excel. */
  readonly isPartial: boolean;
  readonly groupBy: ReportGrouping;
  readonly issuer: ReportIssuer;
}

export interface ContractPeriodReport extends ReportHeader {
  readonly reportType: 'CONTRACT_PERIOD';
  /** Maior que zero acrescenta o aviso de reabertura (FA-03, CA-04). */
  readonly reopenCount: number;
  readonly snapshotAt: string | null;
  readonly client: ReportClient;
  readonly contract: ReportContract;
  readonly period: ReportPeriod;
  readonly balance: ReportBalance;
  readonly adjustments: readonly ReportAdjustment[];
  readonly financial: ReportFinancial | null;
  readonly groups: readonly ReportGroup[];
  readonly summaries: ReportSummaries;
  readonly totals: ReportTotals;
}

export interface ClientSummaryContractSection {
  readonly contract: ReportContract;
  readonly currency: string;
  readonly balance: ReportBalance;
  readonly financial: ReportFinancial | null;
  readonly totals: ReportTotals;
}

/** CE-R-09: totais separados por moeda; não há conversão. */
export interface ClientSummaryCurrencyTotal {
  readonly currency: string;
  readonly netMinutes: number;
  readonly durationLabel: string;
  readonly totalValue: number | null;
}

export interface ClientSummaryReport extends ReportHeader {
  readonly reportType: 'CLIENT_SUMMARY';
  readonly client: ReportClient;
  readonly range: ReportRange;
  readonly contracts: readonly ClientSummaryContractSection[];
  readonly totalsByCurrency: readonly ClientSummaryCurrencyTotal[];
  readonly groups: readonly ReportGroup[];
  readonly summaries: ReportSummaries;
  readonly totals: ReportTotals;
}

export interface TimesheetReport extends ReportHeader {
  readonly reportType: 'TIMESHEET';
  readonly range: ReportRange;
  readonly groups: readonly ReportGroup[];
  readonly summaries: ReportSummaries;
  readonly totals: ReportTotals;
}

export interface ReportTicket {
  readonly key: string;
  readonly title: string;
  readonly status: string;
  readonly priority: string;
  readonly estimatedMinutes: number | null;
  readonly spentMinutes: number;
}

export interface TicketDetailReport extends ReportHeader {
  readonly reportType: 'TICKET_DETAIL';
  readonly client: ReportClient;
  readonly contract: ReportContract;
  readonly ticket: ReportTicket;
  readonly groups: readonly ReportGroup[];
  readonly summaries: ReportSummaries;
  readonly totals: ReportTotals;
}

/** IDG-02: valores absolutos, em ordem alfabética. Nunca ranking. */
export interface ProductivityByUser {
  readonly userName: string;
  readonly netMinutes: number;
  readonly billableMinutes: number;
  readonly durationLabel: string;
  readonly decimalHours: number;
  readonly billableRate: number;
  readonly minutesPerWorkDay: number;
}

export interface ProductivityByWeek {
  readonly isoWeek: string;
  readonly weekStart: string;
  readonly weekEnd: string;
  readonly netMinutes: number;
  readonly billableMinutes: number;
  readonly durationLabel: string;
}

export interface ProductivityReport extends ReportHeader {
  readonly reportType: 'PRODUCTIVITY';
  readonly range: ReportRange;
  readonly workDays: number;
  readonly byUser: readonly ProductivityByUser[];
  readonly byWeek: readonly ProductivityByWeek[];
  readonly summaries: ReportSummaries;
  readonly totals: ReportTotals;
}

/**
 * Qualquer um dos cinco relatórios.
 *
 * A união é discriminada por `reportType`, e é isso que permite ao visualizador decidir o que
 * renderizar sem receber um segundo parâmetro que poderia divergir do conteúdo.
 */
export type Report =
  | ContractPeriodReport
  | ClientSummaryReport
  | TimesheetReport
  | TicketDetailReport
  | ProductivityReport;

/** Relatórios que trazem detalhamento em grupos — todos menos produtividade. */
export type GroupedReport =
  | ContractPeriodReport
  | ClientSummaryReport
  | TimesheetReport
  | TicketDetailReport;

/**
 * Filtros e opções de composição (`ReportFilters` do backend).
 *
 * Nenhum campo aceita `tenantId` nem identificador de solicitante (ART-021, BR-090): o escopo é
 * derivado do papel pelo servidor.
 */
export interface ReportFilters {
  readonly groupBy?: ReportGrouping;
  readonly includeNonBillable?: boolean;
  readonly includeFinancial?: boolean;
  /** `undefined` é o `auto` de §6: a coluna aparece só com mais de um usuário no resultado. */
  readonly includeUserColumn?: boolean;
  readonly from?: string;
  readonly to?: string;
  readonly contractIds?: readonly string[];
  readonly clientIds?: readonly string[];
  readonly categoryIds?: readonly string[];
  readonly tagIds?: readonly string[];
  readonly userIds?: readonly string[];
  readonly billable?: boolean;
}

/**
 * O recorte pedido pela tela: o tipo, o alvo que o tipo exige e os filtros.
 *
 * Os três identificadores são mutuamente exclusivos por tipo, exatamente como em `ExportParameters`
 * — é o mesmo recorte que vai para a consulta e para a exportação, e mantê-los num tipo só é o que
 * garante que o arquivo reproduza a tela (§8.1).
 */
export interface ReportCriteria {
  readonly reportType: ReportType;
  readonly contractPeriodId?: string;
  readonly clientId?: string;
  readonly ticketId?: string;
  readonly filters: ReportFilters;
}

export interface ExportOptions {
  readonly fileName?: string;
  readonly coverPage?: boolean;
  readonly includeSummaryCharts?: boolean;
  readonly language?: string;
}

export interface ExportRequest {
  readonly reportType: ReportType;
  readonly format: ExportFormat;
  readonly parameters: {
    readonly contractPeriodId?: string;
    readonly clientId?: string;
    readonly ticketId?: string;
    readonly filters: ReportFilters;
  };
  readonly options?: ExportOptions;
}

/** Resposta de `POST /reports/exports` nos dois modos (§8.1). */
export interface ExportResponse {
  readonly id: string;
  readonly status: ExportStatus;
  readonly format: ExportFormat;
  readonly fileName: string | null;
  readonly sizeBytes: number | null;
  readonly rowCount: number | null;
  readonly estimatedRowCount: number | null;
  /** Presente apenas no modo síncrono; assinada, com 15 minutos (RN-712). */
  readonly downloadUrl: string | null;
  readonly pollUrl: string | null;
  readonly expiresAt: string | null;
  readonly generatedAt: string | null;
  readonly message: string | null;
}

export interface ExportProgress {
  readonly processedRows: number;
  readonly totalRows: number;
  readonly percentage: number;
}

export interface ExportExecution {
  readonly id: string;
  readonly status: ExportStatus;
  readonly reportType: ReportType;
  readonly format: ExportFormat;
  readonly requestedBy: ReportUserRef;
  /** RN-707: os filtros aplicados, devolvidos para reprodutibilidade. */
  readonly parameters: string | null;
  readonly progress: ExportProgress | null;
  readonly rowCount: number | null;
  readonly fileName: string | null;
  readonly sizeBytes: number | null;
  readonly attemptCount: number;
  readonly failureReason: string | null;
  readonly createdAt: string;
  readonly completedAt: string | null;
  readonly expiresAt: string | null;
}

/** §4.10: o polling só faz sentido enquanto a exportação não parou. */
export function isExportPending(status: ExportStatus): boolean {
  return status === 'QUEUED' || status === 'PROCESSING';
}

/** RN-705 verificado no cliente, para não gastar uma viagem até o `DEVTIME-3001`. */
export function rangeDays(from: string, to: string): number {
  const start = Date.parse(`${from}T00:00:00Z`);
  const end = Date.parse(`${to}T00:00:00Z`);
  if (Number.isNaN(start) || Number.isNaN(end)) {
    return Number.NaN;
  }
  return Math.floor((end - start) / 86_400_000) + 1;
}
