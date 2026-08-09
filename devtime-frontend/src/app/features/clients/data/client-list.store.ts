import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { emptyPage, PageResponse } from '../../../shared/models/page.model';
import { ClientApi } from './client.api';
import { ClientListItem, ClientListQuery } from './client.model';

/**
 * Estado da listagem de clientes (T-003-16, P10).
 *
 * ST-01 / FR-041: Signals de escrita privados, exposição por `asReadonly()`.
 * ST-03 / FR-043: `loading` e `error` obrigatórios.
 *
 * **Os filtros não vivem aqui.** §6.1 de `frontend.md` coloca filtro, paginação e ordenação na URL:
 * guardá-los também no store criaria duas verdades sobre o que está sendo exibido, e a listagem
 * deixaria de ser recuperável por link. O store recebe a consulta pronta e devolve a página.
 *
 * Provido na rota, não em `root`: o resultado morre com a tela e trocar de organização não deixa
 * clientes de outro tenant em memória (FR-051).
 */
@Injectable()
export class ClientListStore {
  private readonly api = inject(ClientApi);

  private readonly _page = signal<PageResponse<ClientListItem>>(emptyPage<ClientListItem>());
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly page = this._page.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly clients = computed(() => this._page().content);

  readonly total = computed(() => this._page().totalElements);

  /** LS-02: a barra de totais fala do conjunto filtrado, e é o servidor quem o conhece. */
  readonly activeCount = computed(
    () => this.clients().filter((client) => client.status === 'ACTIVE').length,
  );

  readonly isEmpty = computed(() => !this._loading() && this._page().totalElements === 0);

  async load(query: ClientListQuery): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      this._page.set(await firstValueFrom(this.api.list(query)));
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      // A página anterior é descartada: manter linhas de um filtro que falhou faria a tela mostrar
      // um resultado que não corresponde ao que está na URL.
      this._page.set(emptyPage<ClientListItem>(query.size));
    } finally {
      this._loading.set(false);
    }
  }
}
