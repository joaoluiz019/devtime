import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { PeriodApi } from './period.api';
import { StatementEntry } from './period.model';

/** Lançamentos carregados por vez. O extrato de um período pode passar de 5.000 linhas (§20). */
const PAGE_SIZE = 50;

/**
 * Extrato do período (§21.3 de `specs/011-bank-hours/spec.md`, T-011-14).
 *
 * **Paginação no cliente, deliberadamente.** `GET /contract-periods/{id}/statement` devolve
 * `entries[]` inteiro, sem cursor nem parâmetros de página — foi o que o backend publicou. Enquanto
 * o endpoint não aceitar cursor, paginar no servidor é impossível e renderizar 5.000 linhas de uma
 * vez violaria FR-161. A janela local é a única opção que respeita as duas regras; a lacuna está
 * registrada no relatório da sprint.
 */
@Injectable()
export class StatementStore {
  private readonly api = inject(PeriodApi);

  private readonly _entries = signal<readonly StatementEntry[]>([]);
  private readonly _visible = signal(PAGE_SIZE);
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  /** Janela visível do extrato. */
  readonly entries = computed(() => this._entries().slice(0, this._visible()));

  readonly total = computed(() => this._entries().length);

  readonly hasMore = computed(() => this._visible() < this._entries().length);

  /** Soma dos lançamentos: precisa bater com o saldo, e o teste T-011-37 verifica exatamente isso. */
  readonly sumMinutes = computed(() =>
    this._entries().reduce((total, entry) => total + entry.minutes, 0),
  );

  async load(periodId: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const statement = await firstValueFrom(this.api.statement(periodId));
      this._entries.set(statement.entries);
      this._visible.set(PAGE_SIZE);
    } catch (error) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
    } finally {
      this._loading.set(false);
    }
  }

  loadMore(): void {
    this._visible.update((visible) => visible + PAGE_SIZE);
  }
}
