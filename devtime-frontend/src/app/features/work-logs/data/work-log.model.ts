import { PeriodBalance } from '../../../shared/models/balance.model';
import { TagOption } from '../../../shared/data/tag-lookup.api';

/** Origem do registro: manual, cronômetro ou importação (RN-126: imutável). */
export type WorkLogSource = 'MANUAL' | 'TIMER' | 'IMPORT';

export interface WorkLogTicketRef {
  readonly id: string;
  readonly key: string;
  readonly title: string;
}

export interface WorkLogCategoryRef {
  readonly id: string;
  readonly name: string;
  readonly color: string;
}

export interface WorkLogUser {
  readonly id: string;
  readonly name: string;
  readonly avatarUrl?: string;
}

export interface WorkLog {
  readonly id: string;
  readonly ticket: WorkLogTicketRef;
  readonly contractId: string;
  readonly clientId: string;
  readonly contractPeriodId: string;
  readonly user: WorkLogUser;
  readonly category?: WorkLogCategoryRef;
  /** RN-108: data local do tenant, já resolvida pelo servidor. */
  readonly workDate: string;
  readonly startedAt: string;
  readonly endedAt: string;
  readonly grossMinutes: number;
  readonly pausedMinutes: number;
  readonly netMinutes: number;
  readonly billableMinutes: number;
  readonly durationLabel: string;
  readonly description: string;
  readonly billable: boolean;
  readonly source: WorkLogSource;
  readonly timerId?: string;
  /** RN-121: preenchido quando o período foi fechado — impede edição e exclusão. */
  readonly lockedAt?: string;
  readonly editCount: number;
  readonly tags: readonly TagOption[];
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
}

export interface WorkLogSummary {
  readonly id: string;
  readonly workDate: string;
  readonly startedAt: string;
  readonly endedAt: string;
  readonly ticketKey: string;
  readonly ticketId: string;
  readonly categoryName?: string;
  readonly userId: string;
  readonly netMinutes: number;
  readonly billableMinutes: number;
  readonly durationLabel: string;
  readonly billable: boolean;
  readonly source: WorkLogSource;
  readonly lockedAt?: string;
}

/** Aviso não bloqueante do servidor — estouro de saldo, por exemplo (RN-231 em política `WARN`). */
export interface WorkLogWarning {
  readonly code: string;
  readonly message: string;
  readonly exceedingMinutes: number;
}

/** RN-102: registro do mesmo usuário que colide no tempo. */
export interface WorkLogConflict {
  readonly id: string;
  readonly workDate: string;
  readonly startedAt: string;
  readonly endedAt: string;
  readonly ticketKey: string;
}

export interface WorkLogCalculation {
  readonly grossMinutes: number;
  readonly pausedMinutes: number;
  readonly netMinutesBeforeRounding: number;
  readonly netMinutes: number;
  readonly billableMinutes: number;
  readonly workDate: string;
  readonly durationLabel: string;
}

/** Efeito do registro sobre o saldo do período, calculado antes de salvar. */
export interface BalancePreview {
  readonly contractPeriodId: string;
  readonly availableMinutes: number;
  readonly consumedBeforeMinutes: number;
  readonly consumedAfterMinutes: number;
  readonly remainingAfterMinutes: number;
}

export interface WorkLogValidation {
  readonly valid: boolean;
  readonly errors: readonly WorkLogWarning[];
  readonly warnings: readonly WorkLogWarning[];
  readonly conflicts: readonly WorkLogConflict[];
  readonly calculated?: WorkLogCalculation;
  readonly balancePreview?: BalancePreview;
}

/** Resposta de criação e edição: o registro, o saldo resultante e os avisos. */
export interface WorkLogSaved {
  readonly workLog: WorkLog;
  readonly balance?: PeriodBalance;
  readonly warnings: readonly WorkLogWarning[];
}

export interface WorkLogCreateRequest {
  readonly ticketId: string;
  readonly startedAt: string;
  readonly endedAt: string;
  readonly pausedMinutes?: number;
  readonly description: string;
  readonly categoryId?: string;
  readonly billable?: boolean;
  readonly tagIds?: readonly string[];
  /** RN-106: lançar para outra pessoa exige permissão; o servidor decide. */
  readonly userId?: string;
}

export interface WorkLogUpdateRequest {
  readonly ticketId: string;
  readonly startedAt: string;
  readonly endedAt: string;
  readonly pausedMinutes?: number;
  readonly description: string;
  readonly categoryId: string;
  readonly billable: boolean;
  readonly tagIds?: readonly string[];
  readonly version: number;
}

export interface WorkLogValidateRequest {
  readonly ticketId: string;
  readonly startedAt: string;
  readonly endedAt: string;
  readonly pausedMinutes?: number;
  readonly description?: string;
  readonly categoryId?: string;
  readonly billable?: boolean;
  readonly userId?: string;
  /** Na edição, o próprio registro não pode contar como conflito consigo mesmo. */
  readonly excludeWorkLogId?: string;
}

export interface WorkLogCategoryTotal {
  readonly categoryId: string;
  readonly categoryName: string;
  readonly totalMinutes: number;
  readonly entryCount: number;
}

/** Totais dos **mesmos** filtros da listagem (P21). */
export interface WorkLogTotals {
  readonly totalMinutes: number;
  readonly billableMinutes: number;
  readonly nonBillableMinutes: number;
  readonly entryCount: number;
  readonly byCategory: readonly WorkLogCategoryTotal[];
}

export interface WorkLogCalendarDay {
  readonly date: string;
  readonly totalMinutes: number;
  readonly billableMinutes: number;
  readonly entryCount: number;
}

export interface WorkLogCalendar {
  readonly from: string;
  readonly to: string;
  readonly days: readonly WorkLogCalendarDay[];
  readonly totalMinutes: number;
}

/** Filtros de P21, que vivem na URL (LS-03). */
export interface WorkLogListQuery {
  readonly userId?: string;
  readonly ticketId?: string;
  readonly contractId?: string;
  readonly clientId?: string;
  readonly categoryId?: string;
  readonly tagIds?: readonly string[];
  readonly dateFrom?: string;
  readonly dateTo?: string;
  readonly billable?: boolean;
  readonly source?: WorkLogSource;
  readonly search?: string;
  readonly page: number;
  readonly size: number;
  readonly sort: string;
}
