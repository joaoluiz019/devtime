import { PeriodStatus } from '../../../shared/models/balance.model';

/**
 * Tipos de contrato, espelhando `ContractRequests` e `ContractResponses` (AP-02 / FR-061).
 *
 * SG-03: os campos monetários chegam **nulos** quando o papel não tem `CONTRACT_VIEW_FINANCIAL` — a
 * omissão é do backend. A tela apenas não desenha o que não veio; nunca deduz valor.
 */
export type ContractType = 'MONTHLY_HOURS' | 'HOURLY_OPEN';

export type ContractStatus = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'ENDED' | 'CANCELLED';

export type RolloverPolicy = 'NONE' | 'FULL' | 'CAPPED';

export type OveragePolicy = 'BLOCK' | 'WARN' | 'ALLOW_BILLABLE';

export interface ContractClient {
  readonly id: string;
  readonly name: string;
  readonly color: string;
}

export interface ContractPeriod {
  readonly id: string;
  readonly sequence: number;
  readonly label: string;
  readonly startDate: string;
  readonly endDate: string;
  readonly status: PeriodStatus;
  readonly contractedMinutes: number;
  readonly carriedInMinutes: number;
  readonly adjustmentMinutes: number;
  readonly consumedMinutes: number;
  readonly nonBillableMinutes: number;
  readonly currency: string;
}

/** Período projetado pela prévia (`PeriodPreviewItem`); ainda não existe no banco. */
export interface PeriodPreviewItem {
  readonly sequence: number;
  readonly label: string;
  readonly startDate: string;
  readonly endDate: string;
  readonly contractedMinutes: number;
  readonly prorated: boolean;
  readonly prorationBasis?: string;
}

export interface Contract {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly description?: string;
  readonly client: ContractClient;
  readonly type: ContractType;
  readonly status: ContractStatus;
  readonly monthlyMinutes?: number;
  readonly startDate: string;
  readonly endDate?: string;
  readonly billingDay: number;
  readonly rolloverPolicy: RolloverPolicy;
  readonly rolloverCapMinutes?: number;
  readonly rolloverExpiryPeriods: number;
  readonly overagePolicy: OveragePolicy;
  /** Nulo sem `CONTRACT_VIEW_FINANCIAL` (SG-03). */
  readonly hourlyRate?: number;
  readonly overageRate?: number;
  readonly currency: string;
  readonly autoRenew: boolean;
  readonly prorateFirstPeriod: boolean;
  readonly notificationThresholds: readonly number[];
  readonly defaultCategoryId?: string;
  readonly notes?: string;
  /** Período `OPEN` corrente; ausente em `DRAFT`, que ainda não gerou período (RN-209). */
  readonly currentPeriod?: ContractPeriod;
  readonly periodsPreview: readonly PeriodPreviewItem[];
  readonly version: number;
  readonly availableTransitions: readonly ContractStatus[];
  /**
   * Ações permitidas pelo estado **e** pela permissão (ME-06).
   *
   * `ACTIVATE_OR_RESUME` é uma ação só no contrato porque a transição de destino é a mesma
   * (`ACTIVE`); o rótulo — "Ativar" ou "Retomar" — depende do estado atual e é decidido na tela.
   */
  readonly availableActions: readonly string[];
}

export interface ContractListItem {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly client: ContractClient;
  readonly type: ContractType;
  readonly status: ContractStatus;
  readonly monthlyMinutes?: number;
  readonly startDate: string;
  readonly endDate?: string;
  readonly currentPeriod?: ContractPeriod;
  readonly version: number;
}

export interface ContractCreateRequest {
  readonly clientId: string;
  readonly code?: string;
  readonly name: string;
  readonly description?: string;
  readonly type: ContractType;
  readonly monthlyMinutes?: number;
  readonly startDate: string;
  readonly endDate?: string;
  readonly billingDay?: number;
  readonly rolloverPolicy?: RolloverPolicy;
  readonly rolloverCapMinutes?: number;
  readonly rolloverExpiryPeriods?: number;
  readonly overagePolicy?: OveragePolicy;
  readonly hourlyRate?: number;
  readonly overageRate?: number;
  readonly currency?: string;
  readonly autoRenew?: boolean;
  readonly prorateFirstPeriod?: boolean;
  readonly notificationThresholds?: readonly number[];
  readonly defaultCategoryId?: string;
  readonly notes?: string;
}

/**
 * `PATCH /contracts/{id}` — RN-206: cliente, tipo e data de início são imutáveis, por isso não
 * aparecem aqui. `applyToCurrentPeriod` é a confirmação de RN-207.
 */
export interface ContractUpdateRequest {
  readonly name: string;
  readonly description?: string;
  readonly monthlyMinutes?: number;
  readonly endDate?: string;
  readonly billingDay?: number;
  readonly rolloverPolicy?: RolloverPolicy;
  readonly rolloverCapMinutes?: number;
  readonly rolloverExpiryPeriods?: number;
  readonly overagePolicy?: OveragePolicy;
  readonly hourlyRate?: number;
  readonly overageRate?: number;
  readonly autoRenew?: boolean;
  readonly notificationThresholds?: readonly number[];
  readonly defaultCategoryId?: string;
  readonly notes?: string;
  readonly applyToCurrentPeriod?: boolean;
  readonly version: number;
}

export interface PeriodPreviewRequest {
  readonly type: ContractType;
  readonly monthlyMinutes?: number;
  readonly startDate: string;
  readonly endDate?: string;
  readonly billingDay: number;
  readonly prorateFirstPeriod?: boolean;
  readonly periodsCount?: number;
}

export interface PeriodPreviewResult {
  readonly periodsPreview: readonly PeriodPreviewItem[];
}

/** Motivo e data das transições (`ContractTransitionRequest`). */
export interface ContractTransitionRequest {
  readonly reason?: string;
  readonly endDate?: string;
  readonly confirmation?: string;
}

export interface ContractActivationResult {
  readonly status: ContractStatus;
  readonly firstPeriod?: ContractPeriod;
}

export interface ContractTransitionResult {
  readonly status: ContractStatus;
  readonly generatedPeriods: readonly ContractPeriod[];
  readonly truncatedPeriod?: ContractPeriod;
}

export interface ContractHistoryPeriod {
  readonly sequence: number;
  readonly label: string;
  readonly status: PeriodStatus;
  readonly contractedMinutes: number;
  readonly carriedInMinutes: number;
  readonly adjustmentMinutes: number;
  readonly consumedMinutes: number;
  readonly remainingMinutes: number;
  readonly overageMinutes: number;
  readonly carriedOutMinutes: number;
}

export interface ContractHistoryAggregates {
  readonly periodsCount: number;
  readonly periodsWithOverage: number;
  readonly totalOverageMinutes: number;
  readonly totalCarriedOutMinutes: number;
}

export interface ContractHistory {
  readonly contractId: string;
  readonly periods: readonly ContractHistoryPeriod[];
  readonly aggregates: ContractHistoryAggregates;
}

/** Filtros de P13, que vivem na URL (LS-03). */
export interface ContractListQuery {
  readonly clientId?: string;
  readonly status?: ContractStatus;
  readonly type?: ContractType;
  readonly search?: string;
  readonly page: number;
  readonly size: number;
  readonly sort: string;
}
