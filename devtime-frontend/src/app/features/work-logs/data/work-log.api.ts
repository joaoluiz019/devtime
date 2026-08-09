import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PageResponse } from '../../../shared/models/page.model';
import {
  WorkLog,
  WorkLogCalendar,
  WorkLogCreateRequest,
  WorkLogListQuery,
  WorkLogSaved,
  WorkLogSummary,
  WorkLogTotals,
  WorkLogUpdateRequest,
  WorkLogValidateRequest,
  WorkLogValidation,
} from './work-log.model';

/**
 * Transporte HTTP dos registros de horas (T-008-26).
 *
 * FR-060 a FR-064: só HTTP. `totals` recebe **os mesmos** filtros de `list` — é o que garante que o
 * total exibido no topo some exatamente as linhas mostradas, inclusive sob o escopo de `MEMBER`.
 */
@Injectable({ providedIn: 'root' })
export class WorkLogApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/work-logs`;

  list(query: WorkLogListQuery): Observable<PageResponse<WorkLogSummary>> {
    const params = this.filterParams(query)
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort);
    return this.http.get<PageResponse<WorkLogSummary>>(this.base, { params });
  }

  totals(query: WorkLogListQuery): Observable<WorkLogTotals> {
    return this.http.get<WorkLogTotals>(`${this.base}/totals`, {
      params: this.filterParams(query),
    });
  }

  /** P22: agrupamento por `workDate`, que já é a data local do tenant (RN-108). */
  calendar(from: string, to: string, userId?: string): Observable<WorkLogCalendar> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (userId !== undefined) {
      params = params.set('userId', userId);
    }
    return this.http.get<WorkLogCalendar>(`${this.base}/calendar`, { params });
  }

  getById(id: string): Observable<WorkLog> {
    return this.http.get<WorkLog>(`${this.base}/${encodeURIComponent(id)}`);
  }

  create(request: WorkLogCreateRequest): Observable<WorkLogSaved> {
    return this.http.post<WorkLogSaved>(this.base, request);
  }

  update(id: string, request: WorkLogUpdateRequest): Observable<WorkLogSaved> {
    return this.http.put<WorkLogSaved>(`${this.base}/${encodeURIComponent(id)}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`);
  }

  /**
   * Validação prévia (P23).
   *
   * Devolve conflitos de sobreposição (RN-102), o cálculo do servidor e o efeito sobre o saldo antes
   * de gravar. É o que permite avisar de estouro **antes** de a pessoa concluir o lançamento.
   */
  validate(request: WorkLogValidateRequest): Observable<WorkLogValidation> {
    return this.http.post<WorkLogValidation>(`${this.base}/validate`, request);
  }

  private filterParams(query: WorkLogListQuery): HttpParams {
    let params = new HttpParams();
    const simple: readonly [string, string | undefined][] = [
      ['userId', query.userId],
      ['ticketId', query.ticketId],
      ['contractId', query.contractId],
      ['clientId', query.clientId],
      ['categoryId', query.categoryId],
      ['dateFrom', query.dateFrom],
      ['dateTo', query.dateTo],
      ['source', query.source],
      ['search', query.search === '' ? undefined : query.search],
    ];
    for (const [key, value] of simple) {
      if (value !== undefined) {
        params = params.set(key, value);
      }
    }
    if (query.billable !== undefined) {
      params = params.set('billable', query.billable);
    }
    for (const tagId of query.tagIds ?? []) {
      params = params.append('tagIds', tagId);
    }
    return params;
  }
}
