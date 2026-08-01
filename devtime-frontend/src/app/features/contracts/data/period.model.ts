import { PeriodBalance, PeriodStatus } from '../../../shared/models/balance.model';

/**
 * Modelos do banco de horas, espelhando os DTOs publicados no OpenAPI (FR-061, AP-02).
 *
 * Nenhuma transformação, nenhum campo derivado e nenhum renome: o que é derivado vive em `computed`
 * no store (FR-042), e o que é formatado vive em pipes (FR-044).
 *
 * `PeriodBalance` **não** está aqui — vive em `shared/models/balance.model.ts` porque os componentes
 * de saldo são compartilhados com `010-dashboard` (FR-004).
 */

/** Espelha `ContractPeriodResponse`. Alimenta `dt-period-timeline`. */
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

/**
 * Tipo de lançamento do extrato, conforme `PeriodStatementEntry.type` publicado.
 *
 * O conjunto entregue pela API é o de **lançamentos** — cada work log é uma linha. A razão contábil
 * de 7 linhas descrita em `docs/04-api/contracts.md` §9.2 (`SUBTOTAL_AVAILABLE`, `CONSUMED`,
 * `BALANCE`, `NON_BILLABLE`) não é emitida; a divergência foi reportada e resolvida em favor do
 * comportamento implementado.
 */
export type StatementEntryType = 'CONTRACTED' | 'CARRIED_IN' | 'ADJUSTMENT' | 'WORK_LOG';

/** Espelha `PeriodStatementEntry`. */
export interface StatementEntry {
  readonly type: StatementEntryType;
  readonly referenceId: string | null;
  readonly date: string;
  readonly description: string;
  /** Positivo credita saldo, negativo consome. */
  readonly minutes: number;
  readonly runningBalanceMinutes: number;
}

/** Espelha `PeriodStatementResponse`. */
export interface PeriodStatement {
  readonly periodId: string;
  readonly balance: PeriodBalance;
  readonly entries: readonly StatementEntry[];
}

/** Motivos de ajuste (`AdjustmentReason` do backend). */
export type AdjustmentReason =
  | 'COURTESY'
  | 'CORRECTION'
  | 'NEGOTIATED_EXTRA'
  | 'PENALTY'
  | 'MIGRATION'
  | 'OTHER';

/** Espelha `AdjustmentRequest`. `appliedBy` e `appliedAt` são sempre do servidor (SG-06). */
export interface AdjustmentRequest {
  readonly minutes: number;
  readonly reason: AdjustmentReason;
  readonly justification: string;
}

/** Espelha `AdjustmentResponse`. Não existe rota de edição nem de exclusão (RN-236). */
export interface Adjustment {
  readonly id: string;
  readonly contractPeriodId: string;
  readonly minutes: number;
  readonly reason: AdjustmentReason;
  readonly justification: string;
  readonly appliedBy: string;
  readonly appliedAt: string;
}

/** Espelha `ClosePeriodRequest`. */
export interface ClosePeriodRequest {
  readonly confirmed: boolean;
  readonly earlyClosingReason: string | null;
}

/** Espelha `ClosePeriodResponse`. */
export interface ClosePeriodResult {
  readonly periodId: string;
  readonly status: PeriodStatus;
  readonly consumedReconciledMinutes: number;
  readonly reconciliationDeltaMinutes: number;
  readonly carriedOutMinutes: number;
  readonly lockedWorkLogs: number;
  readonly snapshotChecksum: string;
  readonly closedAt: string;
}

/** Espelha `ReopenPeriodRequest`. Mínimo de 10 caracteres (RN-242). */
export interface ReopenPeriodRequest {
  readonly reason: string;
}

/** Espelha `ReopenPeriodResponse`. */
export interface ReopenPeriodResult {
  readonly periodId: string;
  readonly status: PeriodStatus;
  readonly reopenCount: number;
  readonly unlockedWorkLogs: number;
  readonly reopenedAt: string;
}

/** Espelha `PeriodSnapshotResponse`. Leitura apenas (INV-SNP-01). */
export interface PeriodSnapshot {
  readonly id: string;
  readonly contractPeriodId: string;
  readonly snapshotAt: string;
  readonly schemaVersion: number;
  readonly checksum: string;
  /** CX-21: falso é alerta operacional, nunca correção automática. */
  readonly checksumValid: boolean;
  readonly payload: string;
}

/**
 * Recorte de `ContractResponse` consumido pelo cabeçalho de P16.
 *
 * P16 precisa apenas de código, nome e cliente para montar a trilha de navegação (DT-01). O modelo
 * completo do contrato pertence à feature `004`, que ainda não tem frontend; declará-lo inteiro
 * aqui criaria uma segunda definição que divergiria da dela.
 */
export interface ContractHeader {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly client: { readonly id: string; readonly name: string };
  readonly status: string;
}
