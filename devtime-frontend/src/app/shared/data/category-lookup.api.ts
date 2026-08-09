import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Categoria como aparece num seletor. */
export interface CategoryOption {
  readonly id: string;
  readonly name: string;
  readonly color: string;
  /** RN-112: define o padrão de faturável do registro de horas. */
  readonly billableByDefault: boolean;
}

interface CategoryResponse extends CategoryOption {
  readonly active: boolean;
}

/**
 * Categorias ativas para seletores de outras features (FR-03).
 *
 * RN-104: categoria inativa não é aceita em novo registro. O filtro é aplicado aqui para que a lista
 * ofereça apenas o que o servidor aceitaria — uma categoria desativada continua existindo em
 * registros antigos, mas não em lançamentos novos.
 */
@Injectable({ providedIn: 'root' })
export class CategoryLookupApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/categories`;

  search(): Observable<readonly CategoryOption[]> {
    return this.http
      .get<readonly CategoryResponse[]>(this.base, { params: new HttpParams().set('active', true) })
      .pipe(map((categories) => categories.filter((category) => category.active !== false)));
  }
}
