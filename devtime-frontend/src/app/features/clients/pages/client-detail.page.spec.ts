import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { axe, toHaveNoViolations } from 'jest-axe';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Client } from '../data/client.model';
import { ClientDetailPage } from './client-detail.page';

expect.extend(toHaveNoViolations);

const CLIENT_ID = '018f2b4c-0000-7000-8000-000000000001';
const CLIENT_URL = `${environment.apiBaseUrl}/clients/${CLIENT_ID}`;

const ACME: Client = {
  id: CLIENT_ID,
  name: 'Acme Corporation',
  legalName: 'Acme Ltda',
  documentType: 'CNPJ',
  documentNumber: '11222333000181',
  email: 'contato@acme.com',
  phone: '+551130000000',
  color: '#4f46e5',
  status: 'ACTIVE',
  activeContractsCount: 2,
  contacts: [
    {
      id: 'c1',
      name: 'Marina Alves',
      email: 'marina@acme.com',
      isPrimary: true,
      receivesReports: true,
      version: 0,
    },
  ],
  createdAt: '2026-07-01T10:00:00Z',
  updatedAt: '2026-07-02T10:00:00Z',
  version: 3,
  availableActions: ['UPDATE', 'DEACTIVATE'],
};

/** P11 — detalhe do cliente (T-003-22). */
describe('ClientDetailPage', () => {
  async function setup() {
    const result = await render(ClientDetailPage, {
      inputs: { id: CLIENT_ID },
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

  /** FR-187: sincronização por microtarefa; o relógio do setup global é falso (FR-185). */
  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  /**
   * O resumo consolidado é acessório e carrega junto com o cliente.
   *
   * Responder com 404 exercita o caminho em que ele não existe — o cadastro precisa continuar
   * legível mesmo assim.
   */
  function flushSummary(http: HttpTestingController): void {
    for (const pending of http.match((request) => request.url.endsWith('/summary'))) {
      pending.flush(null, { status: 404, statusText: 'Not Found' });
    }
  }

  it('exibe o cliente com situação, documento e contatos', async () => {
    const { http } = await setup();
    http.expectOne(CLIENT_URL).flush(ACME);

    expect(await screen.findByRole('heading', { name: /Acme Corporation/ })).toBeVisible();
    expect(screen.getByText('11.222.333/0001-81')).toBeVisible();
    expect(screen.getByText('Marina Alves')).toBeVisible();
    // RN-406: o principal é destacado, e é ele quem recebe os relatórios do cliente.
    expect(screen.getByText('Principal')).toBeVisible();
  });

  it('DT-02 / ME-06: ação fora de availableActions é ocultada', async () => {
    const { http } = await setup();
    http.expectOne(CLIENT_URL).flush(ACME);

    // `p-button` com `routerLink` renderiza um `button`, não uma âncora.
    expect(await screen.findByRole('button', { name: 'Editar' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Inativar' })).toBeVisible();
    // `DELETE` não veio: RN-401 impede a exclusão com contrato ativo.
    expect(screen.queryByRole('button', { name: 'Excluir' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reativar' })).not.toBeInTheDocument();
  });

  it('RN-407: o diálogo declara que os contratos ativos continuam operando', async () => {
    const { http, user } = await setup();
    http.expectOne(CLIENT_URL).flush(ACME);

    await user.click(await screen.findByRole('button', { name: 'Inativar' }));

    // O conteúdo do diálogo é montado em camada própria; a asserção é de presença, não de
    // visibilidade calculada, que depende da animação do PrimeNG.
    expect(await screen.findByText(/continuam operando normalmente/)).toBeInTheDocument();
    // O input do `p-checkbox` fica sob a caixa desenhada pelo tema; o que importa aqui é existir
    // com rótulo associado (A11Y-04).
    expect(
      screen.getByLabelText('Entendi que os contratos ativos continuam operando.'),
    ).toBeInTheDocument();
  });

  it('RN-407: sem a confirmação marcada, nada é enviado', async () => {
    const { http, user } = await setup();
    http.expectOne(CLIENT_URL).flush(ACME);

    await user.click(await screen.findByRole('button', { name: 'Inativar' }));
    await user.click(await screen.findByRole('button', { name: 'Inativar cliente' }));

    expect(await screen.findByText('Confirme para continuar.')).toBeVisible();
    http.expectNone(`${CLIENT_URL}/deactivate`);
  });

  it('inativa com a confirmação e mostra o impacto declarado pelo servidor', async () => {
    const { http, user } = await setup();
    http.expectOne(CLIENT_URL).flush(ACME);

    await user.click(await screen.findByRole('button', { name: 'Inativar' }));
    await user.click(screen.getByLabelText('Entendi que os contratos ativos continuam operando.'));
    await user.click(screen.getByRole('button', { name: 'Inativar cliente' }));

    const request = http.expectOne(`${CLIENT_URL}/deactivate`);
    expect(request.request.body).toEqual({
      confirmActiveContracts: true,
      reason: undefined,
    });
    request.flush({
      status: 'INACTIVE',
      impact: { activeContractsUnaffected: 2, message: '2 contratos seguem operando.' },
    });
    await settle();
    http.expectOne(CLIENT_URL).flush({ ...ACME, status: 'INACTIVE' });
    await settle();
    flushSummary(http);

    expect(await screen.findByText('2 contratos seguem operando.')).toBeVisible();
  });

  it('FA-09: exclusão barrada por contrato ativo sugere a inativação', async () => {
    const { http, user } = await setup();
    http.expectOne(CLIENT_URL).flush({ ...ACME, availableActions: ['UPDATE', 'DELETE'] });

    await user.click(await screen.findByRole('button', { name: 'Excluir' }));
    await settle();
    http.expectOne(CLIENT_URL).flush(
      {
        type: 'about:blank',
        title: 'Conflito',
        status: 409,
        code: 'DEVTIME-2401',
        detail: 'cliente com contrato ativo',
        traceId: 'abc',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(await screen.findByText(/Inative-o para impedir novos contratos/)).toBeVisible();
  });

  it('FR-140: zero violações do axe-core', async () => {
    const { http, container } = await setup();
    http.expectOne(CLIENT_URL).flush(ACME);
    await screen.findByRole('heading', { name: /Acme Corporation/ });

    jest.useRealTimers();
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
