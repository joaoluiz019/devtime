import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { AuthStore } from '../../../core/auth/auth.store';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { WorkLogCalendarPage } from './work-log-calendar.page';

const CALENDAR_URL = `${environment.apiBaseUrl}/work-logs/calendar`;

/**
 * P22 — calendário (T-008-34).
 *
 * O relógio do setup global está fixo em 29/07/2026, então o mês inicial é julho de 2026.
 */
describe('WorkLogCalendarPage', () => {
  async function setup() {
    const result = await render(WorkLogCalendarPage, {
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
      permissions: ['WORKLOG_VIEW_OWN'],
    });

    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
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

  it('pede o mês inteiro em data local, não em UTC', async () => {
    const { http, fixture } = await setup();
    // A sessão é aplicada depois da criação da tela; o efeito recarrega com o usuário conhecido.
    await settle(fixture);

    const requests = http.match((candidate) => candidate.url === CALENDAR_URL);
    const request = requests[requests.length - 1]!;
    // RN-108: `workDate` é data local do tenant; converter para UTC deslocaria o primeiro dia.
    expect(request.request.params.get('from')).toBe('2026-07-01');
    expect(request.request.params.get('to')).toBe('2026-07-31');
    expect(request.request.params.get('userId')).toBe('u1');
    request.flush({ from: '2026-07-01', to: '2026-07-31', days: [], totalMinutes: 0 });
  });

  it('desenha os dias sem registro que o servidor omitiu', async () => {
    const { http } = await setup();

    http
      .expectOne((candidate) => candidate.url === CALENDAR_URL)
      .flush({
        from: '2026-07-01',
        to: '2026-07-31',
        days: [{ date: '2026-07-10', totalMinutes: 300, billableMinutes: 240, entryCount: 2 }],
        totalMinutes: 300,
      });

    // O dia com registro mostra o total; os demais continuam clicáveis, apenas vazios.
    expect(await screen.findByText('05:00')).toBeVisible();
    expect(screen.getByRole('link', { name: /2026-07-11, sem registros/ })).toBeInTheDocument();
  });

  it('navegar de mês refaz a consulta no intervalo novo', async () => {
    const { http, user, fixture } = await setup();
    http
      .expectOne((candidate) => candidate.url === CALENDAR_URL)
      .flush({
        from: '2026-07-01',
        to: '2026-07-31',
        days: [],
        totalMinutes: 0,
      });

    await user.click(await screen.findByRole('button', { name: 'Mês anterior' }));
    await settle(fixture);

    const requests = http.match((candidate) => candidate.url === CALENDAR_URL);
    const request = requests[requests.length - 1]!;
    expect(request.request.params.get('from')).toBe('2026-06-01');
    expect(request.request.params.get('to')).toBe('2026-06-30');
    request.flush({ from: '2026-06-01', to: '2026-06-30', days: [], totalMinutes: 0 });
  });

  it('alternar para todos remove o filtro por usuário', async () => {
    const { http, user, fixture } = await setup();
    http
      .expectOne((candidate) => candidate.url === CALENDAR_URL)
      .flush({
        from: '2026-07-01',
        to: '2026-07-31',
        days: [],
        totalMinutes: 0,
      });

    await user.click(await screen.findByRole('button', { name: 'Todos' }));
    await settle(fixture);

    const remaining = http.match((candidate) => candidate.url === CALENDAR_URL);
    const request = remaining[remaining.length - 1]!;
    expect(request.request.params.has('userId')).toBe(false);
    request.flush({ from: '2026-07-01', to: '2026-07-31', days: [], totalMinutes: 0 });
  });
});
