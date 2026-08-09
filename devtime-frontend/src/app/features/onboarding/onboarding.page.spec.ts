import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { OnboardingPage } from './onboarding.page';

const CLIENTS_URL = `${environment.apiBaseUrl}/clients`;
const CONTRACTS_URL = `${environment.apiBaseUrl}/contracts`;
const TICKETS_URL = `${environment.apiBaseUrl}/tickets`;
const TIMERS_URL = `${environment.apiBaseUrl}/timers`;

/** P08 — do zero ao primeiro registro (CA-01 do PRD). */
describe('OnboardingPage', () => {
  async function setup() {
    const result = await render(OnboardingPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: OnboardingPage }]),
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

  it('WZ-03: cada etapa cria o recurso de verdade ao avançar', async () => {
    const { http, user } = await setup();

    await user.type(screen.getByLabelText('Nome do cliente'), 'Acme Software');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await settle();

    const client = http.expectOne(CLIENTS_URL);
    expect(client.request.body).toEqual({ name: 'Acme Software' });
    client.flush({ id: 'cl-1', name: 'Acme Software' });
    await settle();

    // O nome do contrato já vem preenchido com o do cliente.
    expect(await screen.findByLabelText('Nome do contrato')).toHaveValue('Acme Software');

    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await settle();

    const contract = http.expectOne(CONTRACTS_URL);
    // ART-034: a pergunta é em horas, o contrato viaja em minutos.
    expect(contract.request.body.monthlyMinutes).toBe(1200);
    expect(contract.request.body.clientId).toBe('cl-1');
    expect(contract.request.body.type).toBe('MONTHLY_HOURS');
    contract.flush({ id: 'ct-1' });
    await settle();

    await user.type(await screen.findByLabelText('Título do ticket'), 'Primeira entrega');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await settle();

    const ticket = http.expectOne(TICKETS_URL);
    expect(ticket.request.body).toEqual({ contractId: 'ct-1', title: 'Primeira entrega' });
    ticket.flush({ id: 'tk-1' });
    await settle();

    expect(await screen.findByText('Tudo pronto')).toBeVisible();
  });

  it('WZ-06: a última etapa inicia o cronômetro no ticket recém-criado', async () => {
    const { http, user, router } = await setup();
    const navigate = jest.spyOn(router, 'navigate');

    await user.type(screen.getByLabelText('Nome do cliente'), 'Acme');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await settle();
    http.expectOne(CLIENTS_URL).flush({ id: 'cl-1', name: 'Acme' });
    await screen.findByLabelText('Nome do contrato');

    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await settle();
    http.expectOne(CONTRACTS_URL).flush({ id: 'ct-1' });
    await settle();

    await user.type(await screen.findByLabelText('Título do ticket'), 'Primeira entrega');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await settle();
    http.expectOne(TICKETS_URL).flush({ id: 'tk-1' });
    await settle();

    await user.click(await screen.findByRole('button', { name: 'Iniciar cronômetro' }));
    await settle();

    const timer = http.expectOne((request) => request.url === TIMERS_URL);
    expect(timer.request.body).toEqual({ ticketId: 'tk-1' });
    timer.flush({ id: 'tm-1', status: 'RUNNING', ticket: { id: 'tk-1' } });
    await settle();

    expect(navigate).toHaveBeenCalledWith(['/tickets/tk-1']);
  });

  it('WZ-01: pular está disponível na primeira etapa e leva ao dashboard', async () => {
    const { user, router, http } = await setup();
    const navigate = jest.spyOn(router, 'navigate');

    await user.click(screen.getByRole('button', { name: 'Pular configuração' }));
    await settle();

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
    http.expectNone(CLIENTS_URL);
  });

  it('não avança com o nome do cliente em branco', async () => {
    const { http, user } = await setup();

    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await settle();

    http.expectNone(CLIENTS_URL);
    expect(screen.getByLabelText('Nome do cliente')).toBeVisible();
  });
});
