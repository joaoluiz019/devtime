import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { emptyPage, PageResponse } from '../../../shared/models/page.model';
import { TicketApi } from './ticket.api';
import { TicketListQuery, TicketSummary } from './ticket.model';

/**
 * Estado da listagem de tickets (P17, T-007-28).
 *
 * Os filtros vivem na URL (LS-03); o store recebe a consulta pronta. Provido na rota (FR-051).
 */
@Injectable()
export class TicketListStore {
  private readonly api = inject(TicketApi);

  private readonly _page = signal<PageResponse<TicketSummary>>(emptyPage<TicketSummary>());
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly page = this._page.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly tickets = computed(() => this._page().content);

  readonly total = computed(() => this._page().totalElements);

  readonly isEmpty = computed(() => !this._loading() && this._page().totalElements === 0);

  /** Horas gastas nos tickets exibidos; a barra de totais rotula que é da página. */
  readonly pageSpentMinutes = computed(() =>
    this.tickets().reduce((total, ticket) => total + ticket.spentMinutes, 0),
  );

  /** RN-309: quantos tickets da página já passaram da estimativa. */
  readonly overEstimateCount = computed(
    () => this.tickets().filter((ticket) => ticket.isOverEstimate === true).length,
  );

  async load(query: TicketListQuery): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      this._page.set(await firstValueFrom(this.api.list(query)));
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._page.set(emptyPage<TicketSummary>(query.size));
    } finally {
      this._loading.set(false);
    }
  }
}
