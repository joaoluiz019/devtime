import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { emptyPage, PageResponse } from '../../../shared/models/page.model';
import { WorkLogApi } from './work-log.api';
import { WorkLogListQuery, WorkLogSummary, WorkLogTotals } from './work-log.model';

/**
 * Estado da listagem de registros de horas (P21, T-008-33).
 *
 * LS-02: a barra de totais fala do **conjunto filtrado**, não da página — e por isso vem do endpoint
 * de totais, com os mesmos filtros. Somar as linhas visíveis daria um número menor com aparência de
 * total, que é como uma fatura sai errada.
 */
@Injectable()
export class WorkLogListStore {
  private readonly api = inject(WorkLogApi);

  private readonly _page = signal<PageResponse<WorkLogSummary>>(emptyPage<WorkLogSummary>());
  private readonly _totals = signal<WorkLogTotals | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly page = this._page.asReadonly();
  readonly totals = this._totals.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly entries = computed(() => this._page().content);

  readonly total = computed(() => this._page().totalElements);

  readonly isEmpty = computed(() => !this._loading() && this._page().totalElements === 0);

  /** RN-121: registro de período fechado não aceita edição; a lista o marca. */
  readonly lockedCount = computed(
    () => this.entries().filter((entry) => entry.lockedAt !== undefined).length,
  );

  async load(query: WorkLogListQuery): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      // As duas chamadas partem juntas: a tabela e o total precisam refletir o mesmo filtro, e
      // encadeá-las dobraria o tempo até a tela ficar pronta.
      const [page, totals] = await Promise.all([
        firstValueFrom(this.api.list(query)),
        firstValueFrom(this.api.totals(query)),
      ]);
      this._page.set(page);
      this._totals.set(totals);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._page.set(emptyPage<WorkLogSummary>(query.size));
      this._totals.set(null);
    } finally {
      this._loading.set(false);
    }
  }

  async remove(id: string, query: WorkLogListQuery): Promise<boolean> {
    this._error.set(null);
    try {
      await firstValueFrom(this.api.delete(id));
      await this.load(query);
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    }
  }
}
