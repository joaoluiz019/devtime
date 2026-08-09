import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { AuthStore } from '../../../core/auth/auth.store';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { ClientListItem } from '../data/client.model';
import { ClientListPage } from './client-list.page';

const CLIENTS_URL = `${environment.apiBaseUrl}/clients`;

const ACME: ClientListItem = {
  id: '018f2b4c-0000-7000-8000-000000000001',
  name: 'Acme Corporation',
  legalName: 'Acme Ltda',
  documentType: 'CNPJ',
  documentNumber: '11222333000181',
  email: 'contato@acme.com',
  color: '#4f46e5',
  status: 'ACTIVE',
  activeContractsCount: 2,
  createdAt: '2026-07-01T10:00:00Z',
};

/**
 * P10 — teste de integração com API simulada (FR-182).
 *
 * O ponto central é LS-03: filtro e paginação vivem na URL. Um teste que verificasse apenas "a lista
 * filtrou" passaria com o filtro em estado local e deixaria a tela impossível de compartilhar.
 */
describe('ClientListPage', () => {
  async function setup(permissions: readonly string[] = ['CLIENT_VIEW', 'CLIENT_CREATE']) {
    const result = await render(ClientListPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: ClientListPage }]),
        MessageService,
      ],
    });

    const authStore = result.fixture.debugElement.injector.get(AuthStore);
    authStore.applySession({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresIn: 900,
      tenantSelectionRequired: false,
      user: { id: 'u1', fullName: 'Rafael', displayName: null, email: 'r@e.com', avatarUrl: null },
      permissions: [...permissions],
    });

    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
      router: result.fixture.debugElement.injector.get(Router),
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  /**
   * Deixa a navegação e as promessas pendentes resolverem.
   *
   * FR-187: a sincronização é por microtarefa, nunca por `setTimeout` — o relógio é falso (FR-185).
   */
  async function settle(fixture: {
    detectChanges: () => void;
    whenStable: () => Promise<unknown>;
  }) {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function flush(http: HttpTestingController, content: readonly ClientListItem[] = [ACME]): void {
    http
      .expectOne((request) => request.url === CLIENTS_URL)
      .flush({
        content,
        page: 0,
        size: 20,
        totalElements: content.length,
        totalPages: 1,
        last: true,
      });
  }

  it('consulta a API com a paginação e a ordenação padrão', async () => {
    const { http } = await setup();

    const request = http.expectOne((candidate) => candidate.url === CLIENTS_URL);
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.get('sort')).toBe('name,asc');
    // Parâmetro de filtro ausente não vai como string vazia: `status=` viraria `400` no backend.
    expect(request.request.params.has('status')).toBe(false);
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
  });

  it('exibe os clientes retornados', async () => {
    const { http } = await setup();
    flush(http);

    expect(await screen.findAllByText('Acme Corporation')).not.toHaveLength(0);
    // CX-03: o documento é exibido com máscara, ainda que trafegue sem ela.
    expect(screen.getAllByText('11.222.333/0001-81').length).toBeGreaterThan(0);
  });

  it('LS-03: a busca vai para a URL e refaz a consulta', async () => {
    const { http, user, router, fixture } = await setup();
    flush(http);

    const search = await screen.findByLabelText('Buscar por nome, razão social ou documento');
    await user.type(search, 'acme{Enter}');
    await settle(fixture);

    expect(router.url).toContain('search=acme');
    const request = http.expectOne((candidate) => candidate.url === CLIENTS_URL);
    expect(request.request.params.get('search')).toBe('acme');
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
  });

  it('LS-01: o filtro ativo vira chip removível', async () => {
    const { http, user, router, fixture } = await setup();
    flush(http);

    await user.type(
      await screen.findByLabelText('Buscar por nome, razão social ou documento'),
      'acme{Enter}',
    );
    await settle(fixture);
    flush(http);

    const chip = await screen.findByRole('button', { name: /Busca: acme/ });
    await user.click(chip);
    await settle(fixture);

    expect(router.url).not.toContain('search=acme');
    flush(http);
  });

  it('estado vazio explica o que fazer em vez de mostrar tabela sem linhas', async () => {
    const { http } = await setup();
    flush(http, []);

    expect(await screen.findByRole('heading', { name: 'Nenhum cliente encontrado' })).toBeVisible();
  });

  it('FR-083: sem CLIENT_CREATE a ação de criar é ocultada', async () => {
    const { http } = await setup(['CLIENT_VIEW']);
    flush(http);

    expect(screen.queryByRole('link', { name: 'Novo cliente' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Novo cliente' })).not.toBeInTheDocument();
  });
});
