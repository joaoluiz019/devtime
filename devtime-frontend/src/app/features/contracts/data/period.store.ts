import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { PeriodBalance } from '../../../shared/models/balance.model';
import { criticalityOf } from '../../../shared/utils/criticality';
import { PeriodApi } from './period.api';
import {
  Adjustment,
  AdjustmentRequest,
  ClosePeriodRequest,
  ClosePeriodResult,
  ContractHeader,
  ContractPeriod,
  ReopenPeriodRequest,
  ReopenPeriodResult,
} from './period.model';

/**
 * Estado do período exibido em P16 (§21.3 de `specs/011-bank-hours/spec.md`, T-011-14).
 *
 * ST-01 / FR-041: os Signals de escrita são privados; a exposição é sempre `asReadonly()`.
 * ST-03 / FR-043: `loading` e `error` são obrigatórios.
 * FR-045: nenhuma regra de negócio aqui. `criticality` não recalcula o saldo — apenas classifica a
 * taxa que o servidor entregou, pela tabela normativa §5.3.
 *
 * Provido na rota de P16, não em `root`: o estado morre com a tela, e trocar de organização não
 * deixa saldo de outro tenant em memória (FR-051).
 */
@Injectable()
export class PeriodStore {
  private readonly api = inject(PeriodApi);

  private readonly _balance = signal<PeriodBalance | null>(null);
  private readonly _contract = signal<ContractHeader | null>(null);
  private readonly _periods = signal<readonly ContractPeriod[]>([]);
  private readonly _adjustments = signal<readonly Adjustment[]>([]);
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly balance = this._balance.asReadonly();
  readonly contract = this._contract.asReadonly();
  readonly periods = this._periods.asReadonly();
  readonly adjustments = this._adjustments.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  /** Criticidade da taxa de consumo, pela tabela §5.3 do design system. */
  readonly criticality = computed(() => {
    const balance = this._balance();
    return balance === null ? null : criticalityOf(balance.consumptionRate);
  });

  /** RN-235: ajuste só é permitido em período aberto. */
  readonly canAdjust = computed(() => this._balance()?.status === 'OPEN');

  /** ME-04: fechar só faz sentido em `OPEN` ou `REOPENED`. */
  readonly canClose = computed(() => {
    const status = this._balance()?.status;
    return status === 'OPEN' || status === 'REOPENED';
  });

  /** RN-242: só um período fechado pode ser reaberto. */
  readonly canReopen = computed(() => this._balance()?.status === 'CLOSED');

  /**
   * Fechamento antecipado exige confirmação explícita (RN-239).
   *
   * A comparação é feita sobre a data do período, que o servidor entrega no fuso do tenant. É
   * ergonomia: quem decide se o fechamento é antecipado — e recusa sem `confirmed` — é o
   * `ClosingGuard` no backend.
   */
  readonly isEarlyClosing = computed(() => {
    const balance = this._balance();
    if (balance === null) {
      return false;
    }
    return today() < balance.endDate;
  });

  async load(periodId: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const balance = await firstValueFrom(this.api.balance(periodId));
      this._balance.set(balance);

      // As três consultas restantes são independentes entre si; encadeá-las triplicaria a espera
      // do usuário sem nenhum ganho de consistência.
      const [contract, periods, adjustments] = await Promise.all([
        firstValueFrom(this.api.contract(balance.contractId)),
        firstValueFrom(this.api.periodsOfContract(balance.contractId)),
        firstValueFrom(this.api.adjustments(periodId)),
      ]);
      this._contract.set(contract);
      this._periods.set(periods);
      this._adjustments.set(adjustments);
    } catch (error) {
      this._error.set(asProblemDetail(error));
    } finally {
      this._loading.set(false);
    }
  }

  /**
   * Aplica um ajuste e recarrega o saldo.
   *
   * O saldo **não** é atualizado somando os minutos localmente: a fórmula canônica é do servidor, e
   * reproduzi-la aqui é a origem de divergência que RP-03 identifica como risco crítico. A prévia
   * exibida no diálogo é estimativa de interface; o número que fica na tela vem da API.
   */
  async applyAdjustment(periodId: string, request: AdjustmentRequest): Promise<Adjustment> {
    const created = await firstValueFrom(this.api.applyAdjustment(periodId, request));
    await this.refreshBalance(periodId);
    this._adjustments.set(await firstValueFrom(this.api.adjustments(periodId)));
    return created;
  }

  /** FA-05: o estorno é um novo ajuste de sinal contrário; o original nunca é editado (RN-236). */
  reversalOf(adjustment: Adjustment): AdjustmentRequest {
    return {
      minutes: -adjustment.minutes,
      reason: 'CORRECTION',
      justification: $localize`:@@adjustment.reversal.justification:Estorno do ajuste de ${adjustment.minutes}:minutes: minutos.`,
    };
  }

  async close(periodId: string, request: ClosePeriodRequest): Promise<ClosePeriodResult> {
    const result = await firstValueFrom(this.api.close(periodId, request));
    await this.refreshBalance(periodId);
    return result;
  }

  async reopen(periodId: string, request: ReopenPeriodRequest): Promise<ReopenPeriodResult> {
    const result = await firstValueFrom(this.api.reopen(periodId, request));
    await this.refreshBalance(periodId);
    return result;
  }

  private async refreshBalance(periodId: string): Promise<void> {
    this._balance.set(await firstValueFrom(this.api.balance(periodId)));
  }
}

/** Data de hoje em `yyyy-MM-dd`, comparável com as datas ISO entregues pela API. */
function today(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** O `errorInterceptor` já normaliza a resposta; o fallback cobre falha de rede (CE-F). */
function asProblemDetail(error: unknown): ProblemDetail {
  return isProblemDetail(error) ? error : UNEXPECTED_PROBLEM;
}
