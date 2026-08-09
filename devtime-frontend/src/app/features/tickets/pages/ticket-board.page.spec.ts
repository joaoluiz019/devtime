import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { TicketSummary } from '../data/ticket.model';
import { TicketBoardPage } from './ticket-board.page';

const BOARD_URL = `${environment.apiBaseUrl}/tickets/board`;
const TICKET_ID = '018f2b4c-0000-7000-8000-000000000021';
const TICKET_URL = `${environment.apiBaseUrl}/tickets/${TICKET_ID}`;

const TICKET: TicketSummary = {
  id: TICKET_ID,
  key: 'CT-0001-42',
  title: 'Corrigir cálculo de frete',
  type: 'BUG',
  status: 'TODO',
  priority: 'HIGH',
  contractCode: 'CT-0001',
  spentMinutes: 90,
  estimatedMinutes: 120,
  isOverEstimate: false,
  tags: [],
  updatedAt: '2026-08-01T10:00:00Z',
};

/**
 * P18 — quadro (T-007-29).
 *
 * O teste central é o de acessibilidade por teclado: mover um cartão sem mouse é requisito, não
 * refinamento.
 */
describe('TicketBoardPage', () => {
  async function setup() {
    const result = await render(TicketBoardPage, {
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

  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  function flushBoard(http: HttpTestingController): void {
    http
      .expectOne((request) => request.url === BOARD_URL)
      .flush({
        columns: [
          { status: 'TODO', totalCount: 51, totalSpentMinutes: 90, tickets: [TICKET] },
          { status: 'IN_PROGRESS', totalCount: 0, totalSpentMinutes: 0, tickets: [] },
        ],
      });
  }

  it('desenha todas as colunas do fluxo, inclusive as que o servidor não devolveu', async () => {
    const { http } = await setup();
    flushBoard(http);

    // Sem isto, a coluna vazia sumiria e não haveria destino para mover um cartão até ela.
    for (const column of [
      'Backlog',
      'A fazer',
      'Em andamento',
      'Bloqueado',
      'Em revisão',
      'Concluído',
    ]) {
      expect(await screen.findByRole('heading', { name: column })).toBeVisible();
    }
  });

  it('indica que há cartões além dos 50 exibidos', async () => {
    const { http } = await setup();
    flushBoard(http);

    expect(await screen.findByText(/Mais 50 não exibidos/)).toBeVisible();
  });

  it('T-007-29: o cartão é selecionável por teclado e move sem mouse', async () => {
    const { http, user } = await setup();
    flushBoard(http);

    const card = await screen.findByRole('button', { name: /CT-0001-42/ });
    card.focus();
    await user.keyboard('{Enter}');

    expect(await screen.findByText(/CT-0001-42 selecionado/)).toBeVisible();

    await user.click(await screen.findByRole('button', { name: 'Mover para Em andamento' }));
    await settle();

    // A version é buscada antes da transição: a projeção do quadro não a traz e RN-004 a exige.
    http.expectOne(TICKET_URL).flush({ ...TICKET, version: 7, availableTransitions: [] });
    await settle();
    const transition = http.expectOne(`${TICKET_URL}/transition`);
    expect(transition.request.body).toEqual({
      targetStatus: 'IN_PROGRESS',
      blockReason: undefined,
      version: 7,
    });
    transition.flush({ ...TICKET, status: 'IN_PROGRESS', version: 8 });
    await settle();
    flushBoard(http);
  });

  it('RN-308: mover para bloqueado pede o motivo antes de chamar a API', async () => {
    const { http, user } = await setup();
    flushBoard(http);

    await user.click(await screen.findByRole('button', { name: /CT-0001-42/ }));
    await user.click(await screen.findByRole('button', { name: 'Mover para Bloqueado' }));

    expect(await screen.findByLabelText('Por que está bloqueado? *')).toBeInTheDocument();
    http.expectNone(TICKET_URL);

    await user.click(screen.getByRole('button', { name: 'Bloquear ticket' }));
    expect(
      await screen.findByText('Descreva o impedimento com ao menos 10 caracteres.'),
    ).toBeVisible();
    http.expectNone(TICKET_URL);
  });

  it('RN-311: recusa do servidor mantém o cartão onde estava e explica o motivo', async () => {
    const { http, user } = await setup();
    flushBoard(http);

    await user.click(await screen.findByRole('button', { name: /CT-0001-42/ }));
    await user.click(await screen.findByRole('button', { name: 'Mover para Concluído' }));
    await settle();
    http.expectOne(TICKET_URL).flush({ ...TICKET, version: 3 });
    await settle();
    http.expectOne(`${TICKET_URL}/transition`).flush(
      {
        type: 'about:blank',
        title: 'Conflito',
        status: 409,
        code: 'DEVTIME-2311',
        detail: 'timer ativo',
        traceId: 'abc',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(await screen.findByText('Existe um cronômetro ativo neste ticket.')).toBeVisible();
    // Sem atualização otimista: o cartão continua em "A fazer".
    expect(screen.getByRole('button', { name: /CT-0001-42/ })).toBeVisible();
  });
});
