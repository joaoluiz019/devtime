import { PeriodBalance } from './balance.model';

/**
 * Saldo de exemplo para testes, com os números do **exemplo normativo** de
 * `specs/011-bank-hours/spec.md` §6.1 — o mesmo caso que a suíte do backend reproduz.
 *
 * Usar o exemplo normativo, e não valores arbitrários, faz o teste de interface falhar pelo mesmo
 * motivo que o teste de cálculo falharia: se a exibição divergir do exemplo, a divergência é real.
 */
export function balanceFixture(overrides: Partial<PeriodBalance> = {}): PeriodBalance {
  return {
    periodId: '018f2b4c-0000-7000-8000-000000000001',
    contractId: '018f2b4c-0000-7000-8000-000000000002',
    sequence: 7,
    label: '2026-07',
    startDate: '2026-07-01',
    endDate: '2026-07-31',
    status: 'OPEN',
    contractedMinutes: 2400,
    carriedInMinutes: 300,
    adjustmentMinutes: 60,
    availableMinutes: 2760,
    consumedMinutes: 2900,
    nonBillableMinutes: 195,
    remainingMinutes: -140,
    overageMinutes: 140,
    consumptionRate: 105.07,
    isPartial: true,
    reopenCount: 0,
    currency: 'BRL',
    ...overrides,
  };
}
