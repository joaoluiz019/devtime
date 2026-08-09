import { WorkLogSummary } from '../../work-logs/data/work-log.model';

/**
 * Tipos do painel, espelhando `DashboardResponses` (AP-02 / FR-061).
 *
 * O escopo (`TENANT` ou `USER`) é **derivado do papel pelo servidor**, nunca enviado: um cliente que
 * pedisse `TENANT` estaria pedindo dados de terceiros.
 */
export type DashboardPeriodType = 'CURRENT_PERIOD' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'CUSTOM';

export type DashboardScope = 'TENANT' | 'USER';

export type ContractSeverity = 'OK' | 'INFO' | 'WARNING' | 'CRITICAL';

export type ProjectionStatus = 'WITHIN_LIMIT' | 'AT_RISK' | 'WILL_EXCEED' | 'NOT_APPLICABLE';

export interface DashboardPeriod {
  readonly type: DashboardPeriodType;
  readonly from: string;
  readonly to: string;
}

export interface QuickStats {
  readonly todayMinutes: number;
  readonly todayLabel: string;
  readonly weekMinutes: number;
  readonly weekLabel: string;
  readonly periodMinutes: number;
  readonly periodLabel: string;
  readonly activeTimerMinutes: number;
}

export interface ContractStatus {
  readonly contractId: string;
  readonly code: string;
  readonly name: string;
  readonly clientName: string;
  readonly clientColor: string;
  readonly periodId?: string;
  readonly periodLabel?: string;
  readonly availableMinutes: number;
  readonly consumedMinutes: number;
  readonly remainingMinutes: number;
  readonly consumptionRate: number;
  readonly severity: ContractSeverity;
  readonly daysRemaining: number;
  readonly projectedConsumedMinutes: number;
  readonly projectionStatus: ProjectionStatus;
  /** RN-702: saldo de período aberto é parcial e precisa ser exibido como tal. */
  readonly isPartial: boolean;
}

export interface DashboardAlert {
  readonly type: string;
  readonly severity: ContractSeverity;
  readonly message: string;
  readonly entityType?: string;
  readonly entityId?: string;
}

export interface ChartPoint {
  readonly date: string;
  readonly netMinutes: number;
  readonly billableMinutes: number;
}

export interface ChartSlice {
  readonly entityId?: string;
  readonly label: string;
  readonly color?: string;
  readonly minutes: number;
  readonly percentage: number;
}

export interface DashboardCharts {
  readonly dailyMinutes: readonly ChartPoint[];
  readonly byClient: readonly ChartSlice[];
  readonly byCategory: readonly ChartSlice[];
}

export interface DashboardTicket {
  readonly id: string;
  readonly key: string;
  readonly title: string;
  readonly status: string;
  readonly priority: string;
  readonly contractCode: string;
  readonly dueDate?: string;
  readonly spentMinutes: number;
}

export interface Dashboard {
  readonly period: DashboardPeriod;
  readonly scope: DashboardScope;
  readonly quickStats?: QuickStats;
  readonly contracts: readonly ContractStatus[];
  readonly alerts: readonly DashboardAlert[];
  readonly recentWorkLogs: readonly WorkLogSummary[];
  readonly openTickets: readonly DashboardTicket[];
  readonly charts?: DashboardCharts;
  /**
   * DB-05: blocos que falharam ao montar.
   *
   * O painel responde `200` com o resto — uma falha no gráfico não pode derrubar a tela inteira.
   */
  readonly failedBlocks: readonly string[];
}
