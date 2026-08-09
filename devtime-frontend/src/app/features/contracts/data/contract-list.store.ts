import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { emptyPage, PageResponse } from '../../../shared/models/page.model';
import { ContractApi } from './contract.api';
import { ContractListItem, ContractListQuery } from './contract.model';

/**
 * Estado da listagem de contratos (P13).
 *
 * Como em clientes, os filtros vivem na URL (LS-03) e o store apenas recebe a consulta pronta.
 * Provido na rota (FR-051): o resultado morre com a tela.
 */
@Injectable()
export class ContractListStore {
  private readonly api = inject(ContractApi);

  private readonly _page = signal<PageResponse<ContractListItem>>(emptyPage<ContractListItem>());
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly page = this._page.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly contracts = computed(() => this._page().content);

  readonly total = computed(() => this._page().totalElements);

  readonly isEmpty = computed(() => !this._loading() && this._page().totalElements === 0);

  /**
   * Minutos contratados na página corrente.
   *
   * É **da página**, não do conjunto filtrado: o endpoint de listagem não devolve esse total, e
   * somar as linhas visíveis e chamar de total do filtro seria apresentar um número errado com
   * aparência de certo. A tela rotula assim.
   */
  readonly pageContractedMinutes = computed(() =>
    this.contracts().reduce((total, contract) => total + (contract.monthlyMinutes ?? 0), 0),
  );

  async load(query: ContractListQuery): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      this._page.set(await firstValueFrom(this.api.list(query)));
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._page.set(emptyPage<ContractListItem>(query.size));
    } finally {
      this._loading.set(false);
    }
  }
}
