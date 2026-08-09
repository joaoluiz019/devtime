import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/page.model';

/** Cliente como aparece num seletor: o mínimo para escolher e reconhecer. */
export interface ClientOption {
  readonly id: string;
  readonly name: string;
  readonly color: string;
}

/**
 * Busca de clientes para seletores de outras features (FR-03).
 *
 * A feature de contratos precisa escolher um cliente, mas **não pode importar** de
 * `features/clients` — o compartilhamento entre features passa por `shared`. Este serviço expõe
 * apenas o recorte necessário para um seletor; quem precisa do cadastro inteiro usa a feature de
 * clientes.
 *
 * RN-201: só clientes `ACTIVE` aceitam contrato novo, então o filtro é aplicado na origem. Oferecer
 * um cliente inativo na lista produziria um `422` depois de o formulário inteiro estar preenchido.
 */
@Injectable({ providedIn: 'root' })
export class ClientLookupApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/clients`;

  search(term?: string): Observable<readonly ClientOption[]> {
    let params = new HttpParams().set('status', 'ACTIVE').set('size', 50).set('sort', 'name,asc');
    if (term !== undefined && term !== '') {
      params = params.set('search', term);
    }
    return this.http
      .get<PageResponse<ClientOption>>(this.base, { params })
      .pipe(map((page) => page.content));
  }
}
