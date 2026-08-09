import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { AuthStore } from '../../../core/auth/auth.store';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { ContractListItem } from '../data/contract.model';
import { ContractListPage } from './contract-list.page';

const CONTRACTS_URL = `${environment.apiBaseUrl}/contracts`;

const MONTHLY: ContractListItem = {
  id: '018f2b4c-0000-7000-8000-000000000010',
  code: 'CT-0001',
  name: 'Sustentação Mensal',
  client: { id: 'client-1', name: 'Acme Corporation', color: '#4f46e5' },
  type: 'MONTHLY_HOURS',
  status: 'ACTIVE',
  monthlyMinutes: 2400,
  startDate: '2026-01-01',
  version: 1,
};

const HOURLY: ContractListItem = {
  ...MONTHLY,
  id: '018f2b4c-0000-7000-8000-000000000011',
  code: 'CT-0002',
  name: 'Demandas avulsas',
  type: 'HOURLY_OPEN',
  monthlyMinutes: undefined,
  status: 'DRAFT',
};

/** P13 — lista de contratos (T-004-20). */
describe('ContractListPage', () => {
  async function setup(permissions: readonly string[] = ['CONTRACT_VIEW', 'CONTRACT_CREATE']) {
    const result = await render(ContractListPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: ContractListPage }]),
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

  function flush(
    http: HttpTestingController,
    content: readonly ContractListItem[] = [MONTHLY, HOURLY],
  ): void {
    http
      .expectOne((request) => request.url === CONTRACTS_URL)
      .flush({
        content,
        page: 0,
        size: 20,
        totalElements: content.length,
        totalPages: 1,
        last: true,
      });
  }

  it('consulta com ordenação por código e sem filtros vazios', async () => {
    const { http } = await setup();

    const request = http.expectOne((candidate) => candidate.url === CONTRACTS_URL);
    expect(request.request.params.get('sort')).toBe('code,asc');
    expect(request.request.params.has('status')).toBe(false);
    expect(request.request.params.has('clientId')).toBe(false);
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
  });

  it('INV-CTR-03: contrato por hora não exibe teto mensal', async () => {
    const { http } = await setup();
    flush(http);

    expect(await screen.findByText('Sustentação Mensal')).toBeVisible();
    // 2400 minutos = 40:00 no formato de duração do produto.
    expect(screen.getByText('40:00')).toBeVisible();
    expect(screen.getByText('Por hora')).toBeVisible();
  });

  it('LS-03: filtro de situação vai para a URL e refaz a consulta', async () => {
    const { http, router, fixture } = await setup();
    flush(http);

    await router.navigate([], { queryParams: { status: 'DRAFT' } });
    await settle(fixture);

    const request = http.expectOne((candidate) => candidate.url === CONTRACTS_URL);
    expect(request.request.params.get('status')).toBe('DRAFT');
    request.flush({
      content: [HOURLY],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });

    expect(await screen.findByRole('button', { name: /Rascunho/ })).toBeVisible();
  });

  it('o filtro por cliente na URL é repassado à API', async () => {
    const { http, router, fixture } = await setup();
    flush(http);

    await router.navigate([], { queryParams: { clientId: 'client-1' } });
    await settle(fixture);

    const request = http.expectOne((candidate) => candidate.url === CONTRACTS_URL);
    expect(request.request.params.get('clientId')).toBe('client-1');
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
  });

  it('FR-083: sem CONTRACT_CREATE a ação de criar é ocultada', async () => {
    const { http } = await setup(['CONTRACT_VIEW']);
    flush(http);

    expect(screen.queryByRole('button', { name: 'Novo contrato' })).not.toBeInTheDocument();
  });
});
