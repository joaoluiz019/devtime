import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { DashboardApi } from './dashboard.api';
import { Dashboard, DashboardPeriodType } from './dashboard.model';

/**
 * Estado do painel (P09).
 *
 * DB-05: `failedBlocks` diz quais seções não puderam ser montadas. A tela mostra o restante e
 * assinala a lacuna — esconder a falha faria o usuário ler zero como resultado.
 */
@Injectable()
export class DashboardStore {
  private readonly api = inject(DashboardApi);

  private readonly _dashboard = signal<Dashboard | null>(null);
  private readonly _period = signal<DashboardPeriodType>('CURRENT_PERIOD');
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly dashboard = this._dashboard.asReadonly();
  readonly period = this._period.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  /** Cartões ordenados por severidade e limitados a 6 (P09, seção 2). */
  readonly contracts = computed(() => {
    const order: Record<string, number> = { CRITICAL: 0, WARNING: 1, INFO: 2, OK: 3 };
    return [...(this._dashboard()?.contracts ?? [])]
      .sort((left, right) => (order[left.severity] ?? 9) - (order[right.severity] ?? 9))
      .slice(0, 6);
  });

  readonly alerts = computed(() => this._dashboard()?.alerts ?? []);

  readonly recentWorkLogs = computed(() => this._dashboard()?.recentWorkLogs.slice(0, 5) ?? []);

  readonly openTickets = computed(() => this._dashboard()?.openTickets.slice(0, 5) ?? []);

  readonly failedBlocks = computed(() => this._dashboard()?.failedBlocks ?? []);

  /** `MEMBER` recebe escopo `USER`: sem visão consolidada da organização. */
  readonly isUserScope = computed(() => this._dashboard()?.scope === 'USER');

  readonly isEmpty = computed(
    () => this._dashboard() !== null && this.contracts().length === 0 && this.alerts().length === 0,
  );

  async load(period: DashboardPeriodType = this._period()): Promise<void> {
    this._period.set(period);
    this._loading.set(true);
    this._error.set(null);
    try {
      this._dashboard.set(await firstValueFrom(this.api.load(period)));
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._dashboard.set(null);
    } finally {
      this._loading.set(false);
    }
  }
}
