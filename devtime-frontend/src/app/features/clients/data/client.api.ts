import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PageResponse } from '../../../shared/models/page.model';
import {
  Client,
  ClientCreateRequest,
  ClientSummary,
  ClientDeactivationResult,
  ClientListItem,
  ClientListQuery,
  ClientUpdateRequest,
  Contact,
  ContactRequest,
} from './client.model';

/**
 * Transporte HTTP de clientes e contatos (T-003-15).
 *
 * FR-060 a FR-064: só HTTP. Sem transformação de dado e sem tratamento de erro — os interceptors
 * registrados em `app.config.ts` cuidam de sessão, tenant, retry e `ProblemDetail`.
 */
@Injectable({ providedIn: 'root' })
export class ClientApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/clients`;

  list(query: ClientListQuery): Observable<PageResponse<ClientListItem>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort);

    // Parâmetro ausente e parâmetro vazio não são a mesma coisa para o backend: `status=` viraria
    // uma conversão de enum inválida e responderia `400`.
    if (query.search !== undefined && query.search !== '') {
      params = params.set('search', query.search);
    }
    if (query.status !== undefined) {
      params = params.set('status', query.status);
    }
    if (query.hasActiveContracts !== undefined) {
      params = params.set('hasActiveContracts', query.hasActiveContracts);
    }
    if (query.documentNumber !== undefined && query.documentNumber !== '') {
      params = params.set('documentNumber', query.documentNumber);
    }

    return this.http.get<PageResponse<ClientListItem>>(this.base, { params });
  }

  getById(id: string): Observable<Client> {
    return this.http.get<Client>(`${this.base}/${encodeURIComponent(id)}`);
  }

  create(request: ClientCreateRequest): Observable<Client> {
    return this.http.post<Client>(this.base, request);
  }

  update(id: string, request: ClientUpdateRequest): Observable<Client> {
    return this.http.put<Client>(`${this.base}/${encodeURIComponent(id)}`, request);
  }

  activate(id: string): Observable<Client> {
    return this.http.post<Client>(`${this.base}/${encodeURIComponent(id)}/activate`, null);
  }

  deactivate(
    id: string,
    request: { confirmActiveContracts?: boolean; reason?: string },
  ): Observable<ClientDeactivationResult> {
    return this.http.post<ClientDeactivationResult>(
      `${this.base}/${encodeURIComponent(id)}/deactivate`,
      request,
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`);
  }

  /**
   * clients.md §8: totais consolidados do cliente no período corrente.
   *
   * SM-01: os campos monetários são omitidos pelo servidor sem `CONTRACT_VIEW_FINANCIAL`.
   */
  summary(clientId: string, periods = 6): Observable<ClientSummary> {
    return this.http.get<ClientSummary>(`${this.base}/${encodeURIComponent(clientId)}/summary`, {
      params: new HttpParams().set('periods', periods),
    });
  }

  contacts(clientId: string): Observable<readonly Contact[]> {
    return this.http.get<readonly Contact[]>(
      `${this.base}/${encodeURIComponent(clientId)}/contacts`,
    );
  }

  createContact(clientId: string, request: ContactRequest): Observable<Contact> {
    return this.http.post<Contact>(
      `${this.base}/${encodeURIComponent(clientId)}/contacts`,
      request,
    );
  }

  updateContact(clientId: string, contactId: string, request: ContactRequest): Observable<Contact> {
    return this.http.put<Contact>(
      `${this.base}/${encodeURIComponent(clientId)}/contacts/${encodeURIComponent(contactId)}`,
      request,
    );
  }

  deleteContact(clientId: string, contactId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/${encodeURIComponent(clientId)}/contacts/${encodeURIComponent(contactId)}`,
    );
  }
}
