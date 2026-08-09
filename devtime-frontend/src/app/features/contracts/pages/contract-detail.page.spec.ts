import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Contract } from '../data/contract.model';
import { ContractDetailPage } from './contract-detail.page';

const CONTRACT_ID = '018f2b4c-0000-7000-8000-000000000010';
const CONTRACT_URL = `${environment.apiBaseUrl}/contracts/${CONTRACT_ID}`;

const ACTIVE: Contract = {
  id: CONTRACT_ID,
  code: 'CT-0001',
  name: 'Sustentação Mensal',
  client: { id: 'client-1', name: 'Acme Corporation', color: '#4f46e5' },
  type: 'MONTHLY_HOURS',
  status: 'ACTIVE',
  monthlyMinutes: 2400,
  startDate: '2026-01-01',
  billingDay: 1,
  rolloverPolicy: 'NONE',
  rolloverExpiryPeriods: 0,
  overagePolicy: 'WARN',
  currency: 'BRL',
  autoRenew: true,
  prorateFirstPeriod: true,
  notificationThresholds: [80, 100],
  currentPeriod: {
    id: 'period-1',
    sequence: 7,
    label: '2026-07',
    startDate: '2026-07-01',
    endDate: '2026-07-31',
    status: 'OPEN',
    contractedMinutes: 2400,
    carriedInMinutes: 0,
    adjustmentMinutes: 0,
    consumedMinutes: 1200,
    nonBillableMinutes: 0,
    currency: 'BRL',
  },
  periodsPreview: [],
  version: 2,
  availableTransitions: ['SUSPENDED', 'ENDED', 'CANCELLED'],
  availableActions: ['UPDATE', 'SUSPEND', 'END', 'CANCEL'],
};

const DRAFT: Contract = {
  ...ACTIVE,
  status: 'DRAFT',
  currentPeriod: undefined,
  availableTransitions: ['ACTIVE'],
  availableActions: ['UPDATE', 'DELETE', 'ACTIVATE_OR_RESUME'],
};

/** P14 — detalhe do contrato (T-004-22). */
describe('ContractDetailPage', () => {
  async function setup() {
    const result = await render(ContractDetailPage, {
      inputs: { id: CONTRACT_ID },
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

  /** Um contrato ativo carrega também períodos e histórico. */
  async function flushActive(http: HttpTestingController, contract: Contract = ACTIVE) {
    http.expectOne(CONTRACT_URL).flush(contract);
    await settle();
    http.expectOne(`${CONTRACT_URL}/periods`).flush([contract.currentPeriod]);
    http
      .expectOne((request) => request.url === `${CONTRACT_URL}/history`)
      .flush({
        contractId: CONTRACT_ID,
        periods: [],
        aggregates: {
          periodsCount: 6,
          periodsWithOverage: 1,
          totalOverageMinutes: 120,
          totalCarriedOutMinutes: 0,
        },
      });
  }

  it('exibe cabeçalho com cliente, horas e ciclo', async () => {
    const { http } = await setup();
    await flushActive(http);

    expect(await screen.findByRole('heading', { name: /Sustentação Mensal/ })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Acme Corporation' })).toBeVisible();
    expect(screen.getByText(/Ciclo dia 1/)).toBeVisible();
  });

  it('DRAFT não busca períodos nem histórico e mostra o banner de contrato inativo', async () => {
    const { http } = await setup();
    http.expectOne(CONTRACT_URL).flush(DRAFT);
    await settle();

    // RN-209: períodos só existem depois da ativação; pedi-los aqui traria listas vazias.
    http.expectNone(`${CONTRACT_URL}/periods`);
    expect(await screen.findByText(/Este contrato ainda não está ativo/)).toBeVisible();
  });

  it('ME-06: ações seguem availableActions e o rótulo distingue ativar de retomar', async () => {
    const { http } = await setup();
    http.expectOne(CONTRACT_URL).flush(DRAFT);
    await settle();

    expect(await screen.findByRole('button', { name: 'Ativar' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Excluir' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Retomar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Suspender' })).not.toBeInTheDocument();
  });

  it('RN-209: ativar gera o primeiro período e recarrega o contrato', async () => {
    const { http, user } = await setup();
    http.expectOne(CONTRACT_URL).flush(DRAFT);
    await settle();

    await user.click(await screen.findByRole('button', { name: 'Ativar' }));
    await settle();

    const request = http.expectOne(`${CONTRACT_URL}/activate`);
    expect(request.request.method).toBe('POST');
    request.flush({ status: 'ACTIVE', firstPeriod: ACTIVE.currentPeriod });
    await settle();
    await flushActive(http);

    expect(await screen.findByRole('button', { name: 'Suspender' })).toBeVisible();
  });

  it('DEVTIME-2215: suspender exige justificativa de 10 caracteres antes de chamar a API', async () => {
    const { http, user } = await setup();
    await flushActive(http);

    await user.click(await screen.findByRole('button', { name: 'Suspender' }));
    await user.click(await screen.findByRole('button', { name: 'Suspender contrato' }));

    expect(
      await screen.findByText('A justificativa precisa ter ao menos 10 caracteres.'),
    ).toBeVisible();
    http.expectNone(`${CONTRACT_URL}/suspend`);
  });

  it('suspende com o motivo informado e declara a consequência antes', async () => {
    const { http, user } = await setup();
    await flushActive(http);

    await user.click(await screen.findByRole('button', { name: 'Suspender' }));
    // A consequência é declarada no próprio diálogo, antes do clique de confirmação.
    expect(await screen.findByText(/O período aberto continua aberto/)).toBeInTheDocument();

    await user.type(screen.getByLabelText('Justificativa *'), 'Cliente pediu pausa de dois meses.');
    await user.click(screen.getByRole('button', { name: 'Suspender contrato' }));
    await settle();

    const request = http.expectOne(`${CONTRACT_URL}/suspend`);
    expect(request.request.body).toEqual({
      reason: 'Cliente pediu pausa de dois meses.',
      endDate: undefined,
    });
    request.flush({ status: 'SUSPENDED', generatedPeriods: [] });
    await settle();
    await flushActive(http, { ...ACTIVE, status: 'SUSPENDED', availableActions: ['UPDATE'] });

    expect(await screen.findByText(/Contrato suspenso/)).toBeVisible();
  });

  it('RN-205: exclusão negada por registros exibe a alternativa de encerrar', async () => {
    const { http, user } = await setup();
    http.expectOne(CONTRACT_URL).flush(DRAFT);
    await settle();

    await user.click(await screen.findByRole('button', { name: 'Excluir' }));
    await settle();
    http.expectOne(CONTRACT_URL).flush(
      {
        type: 'about:blank',
        title: 'Conflito',
        status: 409,
        code: 'DEVTIME-2205',
        detail: 'contrato com registros',
        traceId: 'abc',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(await screen.findByText(/Encerre ou cancele/)).toBeVisible();
  });
});
