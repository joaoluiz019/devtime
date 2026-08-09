import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { AuthSessionResponse, TenantOption } from '../../core/auth/auth.model';

import { errorInterceptor } from '../../core/http/error.interceptor';
import { SelectTenantPage } from './select-tenant.page';

const TENANTS_URL = `${environment.apiBaseUrl}/auth/tenants`;
const SELECT_URL = `${environment.apiBaseUrl}/auth/select-tenant`;

const ACME: TenantOption = {
  id: '018f2b4c-0000-7000-8000-000000000001',
  name: 'Acme',
  slug: 'acme',
  role: 'OWNER',
  logoUrl: null,
  status: 'ACTIVE',
};

const GLOBEX: TenantOption = {
  id: '018f2b4c-0000-7000-8000-000000000002',
  name: 'Globex',
  slug: 'globex',
  role: 'MEMBER',
  logoUrl: null,
  status: 'SUSPENDED',
};

const PRE_SELECTION: AuthSessionResponse = {
  accessToken: 'token-de-pre-selecao',
  tokenType: 'Bearer',
  expiresIn: 900,
  tenantSelectionRequired: true,
  user: {
    id: 'u1',
    fullName: 'Rafael Mendes',
    displayName: null,
    email: 'rafael@exemplo.com',
    avatarUrl: null,
  },
  tenants: [ACME, GLOBEX],
};

/** Seleção de organização (P06). */
describe('SelectTenantPage', () => {
  /**
   * O caminho de "lista já veio no login" não é montado aqui: a sessão precisa existir **antes** do
   * `ngOnInit`, e o componente só nasce dentro do `render`. Esse ramo é uma leitura direta de
   * `AuthStore.availableTenants`, coberta em `auth.store.spec.ts`.
   */
  async function setup() {
    const result = await render(SelectTenantPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
      ],
    });
    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  it('busca a lista quando a sessão não a trouxe', async () => {
    const { http } = await setup();

    http.expectOne(TENANTS_URL).flush([ACME, GLOBEX]);

    expect(await screen.findByRole('button', { name: /Acme/ })).toBeVisible();
  });

  it('CX-08: organização suspensa aparece marcada, não omitida', async () => {
    const { http } = await setup();

    http.expectOne(TENANTS_URL).flush([ACME, GLOBEX]);

    expect(await screen.findByRole('button', { name: /Globex/ })).toBeVisible();
    // DS-05: a situação é texto, não apenas cor.
    expect(screen.getByText('Suspensa')).toBeVisible();
  });

  it('RN-008: organização cancelada não é acionável', async () => {
    const { http } = await setup();

    http.expectOne(TENANTS_URL).flush([{ ...GLOBEX, status: 'CANCELLED' }]);

    expect(await screen.findByRole('button', { name: /Globex/ })).toBeDisabled();
  });

  it('seleciona a organização e envia o identificador', async () => {
    const { http, user } = await setup();
    http.expectOne(TENANTS_URL).flush([ACME]);

    await user.click(await screen.findByRole('button', { name: /Acme/ }));

    const request = http.expectOne(SELECT_URL);
    expect(request.request.body).toEqual({ tenantId: ACME.id });
    request.flush({ ...PRE_SELECTION, tenantSelectionRequired: false });
  });

  it('RN-459: vínculo revogado exibe a mensagem do código e mantém a lista', async () => {
    const { http, user } = await setup();
    http.expectOne(TENANTS_URL).flush([ACME]);

    await user.click(await screen.findByRole('button', { name: /Acme/ }));
    http.expectOne(SELECT_URL).flush(
      {
        type: 'about:blank',
        title: 'Proibido',
        status: 403,
        code: 'DEVTIME-1102',
        detail: 'vínculo revogado',
        traceId: 'abc',
      },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(await screen.findByText('Seu acesso a esta organização foi revogado.')).toBeVisible();
    expect(screen.getByRole('button', { name: /Acme/ })).toBeVisible();
  });

  it('INV-USR-04: sem organização ativa, a tela diz o que fazer', async () => {
    const { http } = await setup();

    http.expectOne(TENANTS_URL).flush([]);

    expect(await screen.findByText(/Você não possui acesso ativo/)).toBeVisible();
  });
});
