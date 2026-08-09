import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PageResponse } from '../../../shared/models/page.model';
import {
  Contract,
  ContractActivationResult,
  ContractCreateRequest,
  ContractHistory,
  ContractListItem,
  ContractListQuery,
  ContractPeriod,
  ContractTransitionRequest,
  ContractTransitionResult,
  ContractUpdateRequest,
  PeriodPreviewRequest,
  PeriodPreviewResult,
} from './contract.model';

/**
 * Transporte HTTP de contratos (T-004-15).
 *
 * FR-060 a FR-064: apenas HTTP, sem transformação e sem tratamento de erro.
 */
@Injectable({ providedIn: 'root' })
export class ContractApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/contracts`;

  list(query: ContractListQuery): Observable<PageResponse<ContractListItem>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort);

    if (query.clientId !== undefined) {
      params = params.set('clientId', query.clientId);
    }
    if (query.status !== undefined) {
      params = params.set('status', query.status);
    }
    if (query.type !== undefined) {
      params = params.set('type', query.type);
    }
    if (query.search !== undefined && query.search !== '') {
      params = params.set('search', query.search);
    }

    return this.http.get<PageResponse<ContractListItem>>(this.base, { params });
  }

  getById(id: string): Observable<Contract> {
    return this.http.get<Contract>(`${this.base}/${encodeURIComponent(id)}`);
  }

  create(request: ContractCreateRequest): Observable<Contract> {
    return this.http.post<Contract>(this.base, request);
  }

  /** RN-206: campos imutáveis não viajam; por isso é `PATCH` e não `PUT`. */
  update(id: string, request: ContractUpdateRequest): Observable<Contract> {
    return this.http.patch<Contract>(`${this.base}/${encodeURIComponent(id)}`, request);
  }

  /** CA-01: a prévia usa o mesmo algoritmo da ativação e não persiste nada. */
  previewPeriods(request: PeriodPreviewRequest): Observable<PeriodPreviewResult> {
    return this.http.post<PeriodPreviewResult>(`${this.base}/preview-periods`, request);
  }

  duplicate(id: string, request?: Record<string, unknown>): Observable<Contract> {
    return this.http.post<Contract>(
      `${this.base}/${encodeURIComponent(id)}/duplicate`,
      request ?? null,
    );
  }

  activate(id: string): Observable<ContractActivationResult> {
    return this.http.post<ContractActivationResult>(
      `${this.base}/${encodeURIComponent(id)}/activate`,
      null,
    );
  }

  suspend(id: string, request: ContractTransitionRequest): Observable<ContractTransitionResult> {
    return this.http.post<ContractTransitionResult>(
      `${this.base}/${encodeURIComponent(id)}/suspend`,
      request,
    );
  }

  resume(id: string): Observable<ContractTransitionResult> {
    return this.http.post<ContractTransitionResult>(
      `${this.base}/${encodeURIComponent(id)}/resume`,
      null,
    );
  }

  end(id: string, request: ContractTransitionRequest): Observable<ContractTransitionResult> {
    return this.http.post<ContractTransitionResult>(
      `${this.base}/${encodeURIComponent(id)}/end`,
      request,
    );
  }

  cancel(id: string, request: ContractTransitionRequest): Observable<ContractTransitionResult> {
    return this.http.post<ContractTransitionResult>(
      `${this.base}/${encodeURIComponent(id)}/cancel`,
      request,
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`);
  }

  periods(id: string): Observable<readonly ContractPeriod[]> {
    return this.http.get<readonly ContractPeriod[]>(
      `${this.base}/${encodeURIComponent(id)}/periods`,
    );
  }

  history(id: string, periods = 12): Observable<ContractHistory> {
    return this.http.get<ContractHistory>(`${this.base}/${encodeURIComponent(id)}/history`, {
      params: new HttpParams().set('periods', periods),
    });
  }
}
