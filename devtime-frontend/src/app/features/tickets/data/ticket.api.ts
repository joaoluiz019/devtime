import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PageResponse } from '../../../shared/models/page.model';
import {
  Ticket,
  TicketActivity,
  TicketAssignRequest,
  TicketBoard,
  TicketCreateRequest,
  TicketListQuery,
  TicketMoveContractRequest,
  TicketMoveContractResult,
  TicketSummary,
  TicketTransitionRequest,
  TicketUpdateRequest,
} from './ticket.model';

/**
 * Transporte HTTP de tickets (T-007-23).
 *
 * FR-060 a FR-064: só HTTP. Os filtros de múltipla escolha viajam como parâmetro repetido
 * (`status=TODO&status=DONE`), que é o formato que o Spring liga a `List<T>`.
 */
@Injectable({ providedIn: 'root' })
export class TicketApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/tickets`;

  list(query: TicketListQuery): Observable<PageResponse<TicketSummary>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort);

    params = appendAll(params, 'status', query.status);
    params = appendAll(params, 'type', query.type);
    params = appendAll(params, 'priority', query.priority);
    params = appendAll(params, 'tagIds', query.tagIds);

    if (query.contractId !== undefined) {
      params = params.set('contractId', query.contractId);
    }
    if (query.clientId !== undefined) {
      params = params.set('clientId', query.clientId);
    }
    if (query.assigneeId !== undefined) {
      params = params.set('assigneeId', query.assigneeId);
    }
    if (query.search !== undefined && query.search !== '') {
      params = params.set('search', query.search);
    }
    if (query.isOverEstimate !== undefined) {
      params = params.set('isOverEstimate', query.isOverEstimate);
    }

    return this.http.get<PageResponse<TicketSummary>>(this.base, { params });
  }

  /** Uma consulta agrupada, com no máximo 50 cartões por coluna e `totalCount` real. */
  board(filters: { contractId?: string; assigneeId?: string } = {}): Observable<TicketBoard> {
    let params = new HttpParams();
    if (filters.contractId !== undefined) {
      params = params.set('contractId', filters.contractId);
    }
    if (filters.assigneeId !== undefined) {
      params = params.set('assigneeId', filters.assigneeId);
    }
    return this.http.get<TicketBoard>(`${this.base}/board`, { params });
  }

  getById(id: string): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.base}/${encodeURIComponent(id)}`);
  }

  create(request: TicketCreateRequest): Observable<Ticket> {
    return this.http.post<Ticket>(this.base, request);
  }

  update(id: string, request: TicketUpdateRequest): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.base}/${encodeURIComponent(id)}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`);
  }

  transition(id: string, request: TicketTransitionRequest): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/${encodeURIComponent(id)}/transition`, request);
  }

  assign(id: string, request: TicketAssignRequest): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/${encodeURIComponent(id)}/assign`, request);
  }

  /** RN-305: só sem work logs e apenas para outro contrato do mesmo cliente. */
  moveContract(
    id: string,
    request: TicketMoveContractRequest,
  ): Observable<TicketMoveContractResult> {
    return this.http.post<TicketMoveContractResult>(
      `${this.base}/${encodeURIComponent(id)}/move-contract`,
      request,
    );
  }

  activity(id: string, cursor?: string, size = 50): Observable<TicketActivity> {
    let params = new HttpParams().set('size', size);
    if (cursor !== undefined) {
      params = params.set('cursor', cursor);
    }
    return this.http.get<TicketActivity>(`${this.base}/${encodeURIComponent(id)}/activity`, {
      params,
    });
  }
}

/** Filtro de múltipla escolha vira parâmetro repetido, não lista separada por vírgula. */
function appendAll(
  params: HttpParams,
  key: string,
  values: readonly string[] | undefined,
): HttpParams {
  if (values === undefined || values.length === 0) {
    return params;
  }
  return values.reduce((accumulated, value) => accumulated.append(key, value), params);
}
