import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/page.model';

/** Ticket como aparece num seletor de lançamento de horas. */
export interface TicketOption {
  readonly id: string;
  readonly key: string;
  readonly title: string;
  /** Rótulo pronto para o seletor: a chave é como as pessoas procuram o ticket. */
  readonly label: string;
  readonly contractCode: string;
}

interface TicketSummaryResponse {
  readonly id: string;
  readonly key: string;
  readonly title: string;
  readonly status: string;
  readonly contractCode: string;
}

/**
 * Tickets elegíveis para lançamento de horas (FR-03).
 *
 * RN-101: toda hora pertence a um ticket. Tickets concluídos continuam aceitando lançamento — é
 * comum registrar no dia seguinte o que se terminou ontem —, então o filtro é por busca, não por
 * situação.
 */
@Injectable({ providedIn: 'root' })
export class TicketLookupApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/tickets`;

  search(term?: string): Observable<readonly TicketOption[]> {
    let params = new HttpParams().set('size', 50).set('sort', 'updatedAt,desc');
    if (term !== undefined && term !== '') {
      params = params.set('search', term);
    }
    return this.http.get<PageResponse<TicketSummaryResponse>>(this.base, { params }).pipe(
      map((page) =>
        page.content.map((ticket) => ({
          id: ticket.id,
          key: ticket.key,
          title: ticket.title,
          label: `${ticket.key} — ${ticket.title}`,
          contractCode: ticket.contractCode,
        })),
      ),
    );
  }
}
