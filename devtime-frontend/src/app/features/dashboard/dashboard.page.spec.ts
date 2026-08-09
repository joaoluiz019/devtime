import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { DashboardPage } from './dashboard.page';

const DASHBOARD_URL = `${environment.apiBaseUrl}/dashboard`;

const RESPONSE = {
  period: { type: 'CURRENT_PERIOD', from: '2026-07-01', to: '2026-07-31' },
  scope: 'TENANT',
  quickStats: {
    todayMinutes: 120,
    todayLabel: '02:00',
    weekMinutes: 900,
    weekLabel: '15:00',
    periodMinutes: 2400,
    periodLabel: '40:00',
    activeTimerMinutes: 0,
  },
  contracts: [
    {
      contractId: 'c-ok',
      code: 'CT-0002',
      name: 'Tranquilo',
      clientName: 'Beta',
      clientColor: '#10b981',
      availableMinutes: 2400,
      consumedMinutes: 600,
      remainingMinutes: 1800,
      consumptionRate: 25,
      severity: 'OK',
      daysRemaining: 12,
      projectedConsumedMinutes: 1500,
      projectionStatus: 'WITHIN_LIMIT',
      isPartial: true,
    },
    {
      contractId: 'c-critical',
      code: 'CT-0001',
      name: 'Apertado',
      clientName: 'Acme',
      clientColor: '#4f46e5',
      availableMinutes: 2400,
      consumedMinutes: 2300,
      remainingMinutes: 100,
      consumptionRate: 96,
      severity: 'CRITICAL',
      daysRemaining: 8,
      projectedConsumedMinutes: 3000,
      projectionStatus: 'WILL_EXCEED',
      isPartial: true,
    },
  ],
  alerts: [
    {
      type: 'PERIOD_AT_RISK',
      severity: 'CRITICAL',
      message: 'CT-0001 vai estourar o saldo em 3 dias.',
      entityType: 'CONTRACT',
      entityId: 'c-critical',
    },
  ],
  recentWorkLogs: [],
  openTickets: [],
  charts: {
    dailyMinutes: [
      { date: '2026-07-01', netMinutes: 0, billableMinutes: 0 },
      { date: '2026-07-02', netMinutes: 240, billableMinutes: 180 },
    ],
    byClient: [
      { entityId: 'client-1', label: 'Acme', color: '#4f46e5', minutes: 600, percentage: 60 },
    ],
    byCategory: [
      {
        entityId: 'cat-1',
        label: 'Desenvolvimento',
        color: '#0ea5e9',
        minutes: 400,
        percentage: 40,
      },
    ],
  },
  failedBlocks: [],
};

/** P09 — painel (T-010-16). */
describe('DashboardPage', () => {
  async function setup() {
    const result = await render(DashboardPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: DashboardPage }]),
        MessageService,
      ],
    });
    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
      router: result.fixture.debugElement.injector.get(Router),
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  function flush(http: HttpTestingController, body: unknown = RESPONSE): void {
    http.expectOne((request) => request.url === DASHBOARD_URL).flush(body);
  }

  it('carrega o período corrente por padrão', async () => {
    const { http } = await setup();

    const request = http.expectOne((candidate) => candidate.url === DASHBOARD_URL);
    expect(request.request.params.get('period')).toBe('CURRENT_PERIOD');
    // O escopo é derivado do papel pelo servidor; o cliente nunca o envia.
    expect(request.request.params.has('scope')).toBe(false);
    request.flush(RESPONSE);
  });

  it('ordena os cartões por severidade, com o crítico primeiro', async () => {
    const { http, container } = await setup();
    flush(http);

    await screen.findByText('CT-0001');
    const codes = [...container.querySelectorAll('.dt-contract-card__code')].map((element) =>
      element.textContent?.trim(),
    );
    expect(codes).toEqual(['CT-0001', 'CT-0002']);
  });

  it('a projeção de estouro é dita em texto, não só em cor', async () => {
    const { http } = await setup();
    flush(http);

    expect(await screen.findByText('Vai estourar no ritmo atual')).toBeVisible();
    expect(screen.getByText('Dentro do contratado')).toBeVisible();
  });

  it('DB-05: bloco que falhou é declarado e o resto continua visível', async () => {
    const { http } = await setup();
    flush(http, { ...RESPONSE, charts: undefined, failedBlocks: ['charts'] });

    expect(
      await screen.findByText(/Algumas seções não puderam ser carregadas: charts/),
    ).toBeVisible();
    expect(screen.getByText('CT-0001')).toBeVisible();
  });

  it('trocar o período recarrega o painel', async () => {
    const { http, user } = await setup();
    flush(http);

    await user.click(await screen.findByRole('button', { name: '7 dias' }));
    await settle();

    const request = http.expectOne((candidate) => candidate.url === DASHBOARD_URL);
    expect(request.request.params.get('period')).toBe('LAST_7_DAYS');
    request.flush(RESPONSE);
  });

  it('clicar numa fatia leva à lista de horas filtrada', async () => {
    const { http, user, router } = await setup();
    flush(http);

    await user.click(await screen.findByRole('button', { name: /Acme/ }));
    await settle();

    expect(router.url).toContain('clientId=client-1');
  });

  it('sem contratos oferece o caminho de criar o primeiro', async () => {
    const { http } = await setup();
    flush(http, { ...RESPONSE, contracts: [], alerts: [] });

    expect(await screen.findByRole('heading', { name: 'Nenhum contrato ativo' })).toBeVisible();
  });
});
