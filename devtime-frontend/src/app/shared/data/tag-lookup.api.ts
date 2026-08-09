import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Etiqueta como aparece num seletor (`TagOptionResponse`). */
export interface TagOption {
  readonly id: string;
  readonly name: string;
  readonly color: string;
}

/**
 * Autocompletar de etiquetas para outras features (FR-03).
 *
 * O endpoint dedicado devolve a projeção estreita usada pelos seletores; a listagem completa de
 * etiquetas, com contagens e sugestões de limpeza, pertence à tela de configurações.
 */
@Injectable({ providedIn: 'root' })
export class TagLookupApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/tags`;

  autocomplete(term?: string): Observable<readonly TagOption[]> {
    let params = new HttpParams();
    if (term !== undefined && term !== '') {
      params = params.set('term', term);
    }
    return this.http.get<readonly TagOption[]>(`${this.base}/autocomplete`, { params });
  }
}
