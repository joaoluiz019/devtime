import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/page.model';

/** Contrato como aparece num seletor. */
export interface ContractOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly clientId: string;
  readonly clientName: string;
  readonly acceptsWorkLogs: boolean;
}

interface ContractListItemResponse {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly status: string;
  readonly client: { readonly id: string; readonly name: string };
}

/**
 * Contratos elegíveis para seletores de outras features (FR-03).
 *
 * RN-306: apenas contratos que aceitam trabalho aparecem. `ACTIVE` e `SUSPENDED` são incluídos —
 * suspenso ainda aceita lançamento retroativo dentro da vigência —, enquanto `DRAFT`, `ENDED` e
 * `CANCELLED` ficam de fora: escolher um deles produziria recusa no salvamento.
 */
@Injectable({ providedIn: 'root' })
export class ContractLookupApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/contracts`;

  search(clientId?: string): Observable<readonly ContractOption[]> {
    let params = new HttpParams().set('size', 100).set('sort', 'code,asc');
    if (clientId !== undefined) {
      params = params.set('clientId', clientId);
    }
    return this.http.get<PageResponse<ContractListItemResponse>>(this.base, { params }).pipe(
      map((page) =>
        page.content
          .filter((contract) => contract.status === 'ACTIVE' || contract.status === 'SUSPENDED')
          .map((contract) => ({
            id: contract.id,
            code: contract.code,
            name: `${contract.code} — ${contract.name}`,
            clientId: contract.client.id,
            clientName: contract.client.name,
            acceptsWorkLogs: contract.status === 'ACTIVE',
          })),
      ),
    );
  }
}
