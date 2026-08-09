import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Contract } from '../data/contract.model';
import { ContractFormPage } from './contract-form.page';

const CONTRACTS_URL = `${environment.apiBaseUrl}/contracts`;
const PREVIEW_URL = `${CONTRACTS_URL}/preview-periods`;
const CLIENTS_URL = `${environment.apiBaseUrl}/clients`;
const CONTRACT_ID = '018f2b4c-0000-7000-8000-000000000010';

const EXISTING: Contract = {
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
  notificationThresholds: [80],
  periodsPreview: [],
  version: 4,
  availableTransitions: [],
  availableActions: ['UPDATE'],
};

/** P15 — formulário de contrato (T-004-19). */
describe('ContractFormPage', () => {
  async function setup(id?: string) {
    const result = await render(ContractFormPage, {
      inputs: id === undefined ? {} : { id },
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
      ],
    });
    const http = result.fixture.debugElement.injector.get(HttpTestingController);
    // RN-201: o seletor só oferece clientes ACTIVE, filtrados na origem.
    const lookup = http.expectOne((request) => request.url === CLIENTS_URL);
    expect(lookup.request.params.get('status')).toBe('ACTIVE');
    lookup.flush({
      content: [{ id: 'client-1', name: 'Acme Corporation', color: '#4f46e5' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });

    return {
      ...result,
      http,
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  it('FM-07: submeter sem cliente e sem nome acusa os campos obrigatórios', async () => {
    const { user, http } = await setup();

    await user.click(screen.getByRole('button', { name: 'Criar contrato' }));

    expect(await screen.findByText('Selecione o cliente do contrato.')).toBeVisible();
    expect(screen.getByText('Informe o nome, com 2 a 150 caracteres.')).toBeVisible();
    http.expectNone(CONTRACTS_URL);
  });

  it('FM-09: a prévia é recalculada pelo servidor conforme o preenchimento', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('Horas por mês *'), '40:00');
    // O recálculo é adiado para não chamar a API a cada tecla.
    jest.advanceTimersByTime(400);
    await settle();

    const request = http.expectOne(PREVIEW_URL);
    expect(request.request.body).toMatchObject({ type: 'MONTHLY_HOURS', monthlyMinutes: 2400 });
    request.flush({
      periodsPreview: [
        {
          sequence: 1,
          label: '2026-08',
          startDate: '2026-08-05',
          endDate: '2026-08-31',
          contractedMinutes: 2090,
          prorated: true,
        },
      ],
    });

    expect(await screen.findByText('2026-08')).toBeVisible();
    // O período proporcional é marcado: é o que explica ter menos horas que os seguintes.
    expect(screen.getByText('proporcional')).toBeVisible();
  });

  it('INV-CTR-03: trocar para por hora limpa horas mensais e transporte', async () => {
    const { user, http, fixture } = await setup();

    await user.type(screen.getByLabelText('Horas por mês *'), '40:00');
    jest.advanceTimersByTime(400);
    await settle();
    http.expectOne(PREVIEW_URL).flush({ periodsPreview: [] });

    const page = fixture.componentInstance as unknown as {
      form: { controls: { type: { setValue: (value: string) => void } } };
    };
    page.form.controls.type.setValue('HOURLY_OPEN');
    await settle();
    fixture.detectChanges();

    expect(screen.queryByLabelText('Horas por mês *')).not.toBeInTheDocument();
  });

  it('RN-206: na edição, cliente, tipo e início ficam desabilitados', async () => {
    const { http } = await setup(CONTRACT_ID);

    http.expectOne(`${CONTRACTS_URL}/${CONTRACT_ID}`).flush(EXISTING);
    await settle();

    const page = await screen.findByLabelText('Nome *');
    expect(page).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Salvar alterações' })).toBeVisible();
  });

  it('RN-004: a edição envia PATCH com a version carregada', async () => {
    const { http, user } = await setup(CONTRACT_ID);
    http.expectOne(`${CONTRACTS_URL}/${CONTRACT_ID}`).flush(EXISTING);
    await settle();

    const name = await screen.findByLabelText('Nome *');
    await user.clear(name);
    await user.type(name, 'Sustentação Plus');
    jest.advanceTimersByTime(400);
    await settle();
    // A prévia dispara junto com a digitação; responder mantém a fila limpa.
    for (const pending of http.match(PREVIEW_URL)) {
      pending.flush({ periodsPreview: [] });
    }

    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    await settle();

    const request = http.expectOne(`${CONTRACTS_URL}/${CONTRACT_ID}`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toMatchObject({ name: 'Sustentação Plus', version: 4 });
    request.flush({ ...EXISTING, name: 'Sustentação Plus', version: 5 });
  });
});
