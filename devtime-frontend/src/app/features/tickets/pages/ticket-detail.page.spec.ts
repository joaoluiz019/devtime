import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { AuthStore } from '../../../core/auth/auth.store';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Ticket } from '../data/ticket.model';
import { TicketDetailPage } from './ticket-detail.page';

const TICKET_ID = '018f2b4c-0000-7000-8000-000000000021';
const TICKET_URL = `${environment.apiBaseUrl}/tickets/${TICKET_ID}`;
const MEMBERS_URL = `${environment.apiBaseUrl}/members`;

const TICKET: Ticket = {
  id: TICKET_ID,
  number: 42,
  key: 'CT-0001-42',
  title: 'Corrigir cálculo de frete',
  description: '## Contexto\nO frete **dobra** para o Sul.',
  type: 'BUG',
  status: 'IN_PROGRESS',
  priority: 'HIGH',
  contract: {
    id: 'contract-1',
    code: 'CT-0001',
    name: 'Sustentação',
    status: 'ACTIVE',
    acceptsWorkLogs: true,
  },
  client: { id: 'client-1', name: 'Acme', color: '#4f46e5' },
  reporter: { id: 'u1', name: 'Rafael' },
  spentMinutes: 90,
  billableMinutes: 90,
  estimatedMinutes: 120,
  isOverEstimate: false,
  tags: [],
  createdAt: '2026-07-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
  version: 3,
  availableTransitions: ['IN_REVIEW', 'BLOCKED'],
};

/** P19 — detalhe do ticket (T-007-31). */
describe('TicketDetailPage', () => {
  // `TICKET_UPDATE_ANY` é o nome real no catálogo do servidor. Enquanto este setup usava
  // `TICKET_UPDATE`, o teste passava com a aplicação quebrada: ambos os lados concordavam num nome
  // que o backend nunca emite, e nenhuma permissão real era exercitada.
  async function setup(permissions: readonly string[] = ['TICKET_VIEW', 'TICKET_UPDATE_ANY']) {
    const result = await render(TicketDetailPage, {
      inputs: { id: TICKET_ID },
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
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
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  async function flushTicket(
    http: HttpTestingController,
    ticket: Ticket = TICKET,
    fixture?: { detectChanges: () => void },
  ) {
    // O seletor de responsável só oferece memberships ativos (RN-304).
    for (const pending of http.match((request) => request.url === MEMBERS_URL)) {
      expect(pending.request.params.get('status')).toBe('ACTIVE');
      pending.flush({
        content: [],
        page: 0,
        size: 100,
        totalElements: 0,
        totalPages: 0,
        last: true,
      });
    }
    http.expectOne(TICKET_URL).flush(ticket);
    await settle();
    for (const pending of http.match((request) => request.url.endsWith('/activity'))) {
      pending.flush({ content: [], hasMore: false });
    }
    // O efeito que busca os destinos só dispara na detecção de mudança (ambiente zoneless).
    fixture?.detectChanges();
    await settle();
    // RN-305: os destinos possíveis são buscados por cliente, para o diálogo de mover.
    for (const pending of http.match((request) => request.url.endsWith('/contracts'))) {
      pending.flush({
        content: [
          {
            id: 'contract-2',
            code: 'CT-0002',
            name: 'Outro contrato',
            status: 'ACTIVE',
            client: { id: 'client-1', name: 'Acme' },
          },
        ],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
        last: true,
      });
    }
    await settle();
  }

  it('exibe a descrição renderizada a partir do Markdown', async () => {
    const { http } = await setup();
    await flushTicket(http);

    expect(await screen.findByRole('heading', { name: 'Corrigir cálculo de frete' })).toBeVisible();
    // O Markdown vira marcação de verdade; o `**` não aparece como texto.
    expect(screen.getByRole('heading', { name: 'Contexto' })).toBeVisible();
    expect(screen.getByText('dobra')).toBeVisible();
  });

  it('ME-06: só as transições declaradas pelo servidor viram ação', async () => {
    const { http } = await setup();
    await flushTicket(http);

    expect(await screen.findByRole('button', { name: 'Mover para Em revisão' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Mover para Bloqueado' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Mover para Concluído' })).not.toBeInTheDocument();
  });

  it('RN-004: a transição envia a version corrente', async () => {
    const { http, user } = await setup();
    await flushTicket(http);

    await user.click(await screen.findByRole('button', { name: 'Mover para Em revisão' }));
    await settle();

    const request = http.expectOne(`${TICKET_URL}/transition`);
    expect(request.request.body).toEqual({
      targetStatus: 'IN_REVIEW',
      blockReason: undefined,
      version: 3,
    });
    request.flush({ ...TICKET, status: 'IN_REVIEW', version: 4 });
    await settle();
    for (const pending of http.match((candidate) => candidate.url.endsWith('/activity'))) {
      pending.flush({ content: [], hasMore: false });
    }
  });

  it('RN-306: contrato que não aceita horas é anunciado antes de alguém tentar lançar', async () => {
    const { http } = await setup();
    await flushTicket(http, {
      ...TICKET,
      contract: { ...TICKET.contract, status: 'ENDED', acceptsWorkLogs: false },
    });

    expect(await screen.findByText(/não aceita novos registros de horas/)).toBeVisible();
  });

  it('RN-308: bloquear pede o motivo antes da chamada', async () => {
    const { http, user } = await setup();
    await flushTicket(http);

    await user.click(await screen.findByRole('button', { name: 'Mover para Bloqueado' }));

    expect(await screen.findByLabelText('Por que está bloqueado? *')).toBeInTheDocument();
    http.expectNone(`${TICKET_URL}/transition`);
  });

  it('INV-TKT-01: mover de contrato avisa que a chave não muda', async () => {
    const { http, user, fixture } = await setup();
    await flushTicket(http, TICKET, fixture);
    fixture.detectChanges();

    await user.click(await screen.findByRole('button', { name: 'Mover de contrato' }));

    expect(
      await screen.findByText(/A chave do ticket \(CT-0001-42\) não muda/),
    ).toBeInTheDocument();
  });

  it('FR-083: sem permissão de edição o botão Editar é ocultado', async () => {
    const { http } = await setup(['TICKET_VIEW']);
    await flushTicket(http);

    await screen.findByRole('heading', { name: 'Corrigir cálculo de frete' });
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument();
  });
});
