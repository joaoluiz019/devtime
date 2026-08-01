/**
 * Saldo de um período, espelhando `PeriodBalanceResponse` do backend (FR-061, AP-02).
 *
 * Vive em `shared/models` — e não na feature — porque `dt-balance-summary` e
 * `dt-consumption-gauge` são componentes **compartilhados**, consumidos também por `010-dashboard`
 * (§21.2 de `specs/011-bank-hours/spec.md`, T-010-14). Tipar a entrada deles com um modelo de
 * `features/contracts` faria `shared` depender de uma feature, o que FR-004 proíbe.
 *
 * Nenhum campo é transformado, renomeado ou derivado aqui: a camada de API não transforma dado
 * (FR-062), e o que é derivado vive em `computed` no store.
 */
export interface PeriodBalance {
  readonly periodId: string;
  readonly contractId: string;
  readonly sequence: number;
  readonly label: string;
  readonly startDate: string;
  readonly endDate: string;
  readonly status: PeriodStatus;
  readonly contractedMinutes: number;
  readonly carriedInMinutes: number;
  readonly adjustmentMinutes: number;
  readonly availableMinutes: number;
  readonly consumedMinutes: number;
  readonly nonBillableMinutes: number;
  readonly remainingMinutes: number;
  readonly overageMinutes: number;
  /** Percentual com 2 casas; `number` porque o servidor serializa o `BigDecimal` como número. */
  readonly consumptionRate: number;
  /** RN-702: verdadeiro em `OPEN` e `REOPENED`. Obrigatório na exibição — ver `dt-partial-badge`. */
  readonly isPartial: boolean;
  readonly reopenCount: number;
  readonly currency: string;
}

/** Estados do período (`state-machines.md` §4.6). */
export type PeriodStatus = 'SCHEDULED' | 'OPEN' | 'CLOSING' | 'CLOSED' | 'REOPENED';
