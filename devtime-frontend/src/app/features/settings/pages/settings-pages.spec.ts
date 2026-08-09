import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { ThemeStore } from '../../../core/theme/theme.store';
import { AuditSettingsPage } from './audit-settings.page';
import { NotificationSettingsPage } from './notification-settings.page';
import { PreferencesSettingsPage } from './preferences-settings.page';
import { ProfileSettingsPage } from './profile-settings.page';

const PROFILE_URL = `${environment.apiBaseUrl}/users/me`;
const CATEGORIES_URL = `${environment.apiBaseUrl}/categories`;
const NOTIFICATION_PREFS_URL = `${environment.apiBaseUrl}/notifications/preferences`;
const AUDIT_URL = `${environment.apiBaseUrl}/audit-logs`;

const PROFILE = {
  id: 'u1',
  email: 'rafael@exemplo.com',
  fullName: 'Rafael Mendes',
  displayName: 'Rafa',
  timezone: 'America/Sao_Paulo',
  locale: 'pt-BR',
  preferences: {
    theme: 'DARK',
    dashboardPeriod: 'LAST_7_DAYS',
    emailNotifications: true,
    mutedNotificationTypes: [],
    timerReminderEnabled: true,
  },
  version: 3,
};

function providers() {
  return [
    provideHttpClient(withInterceptors([errorInterceptor])),
    provideHttpClientTesting(),
    provideRouter([]),
    MessageService,
  ];
}

async function settle(): Promise<void> {
  for (let index = 0; index < 5; index += 1) {
    await Promise.resolve();
  }
}

/** P26 — perfil. */
describe('ProfileSettingsPage', () => {
  it('o e-mail é exibido, porém não editável: trocá-lo passa por verificação', async () => {
    const result = await render(ProfileSettingsPage, { providers: providers() });
    const http = result.fixture.debugElement.injector.get(HttpTestingController);
    http.expectOne(PROFILE_URL).flush(PROFILE);

    const email = await screen.findByLabelText('E-mail');
    expect(email).toBeDisabled();
    expect(email).toHaveValue('rafael@exemplo.com');
  });

  it('envia apenas o que foi preenchido, com apelido vazio omitido', async () => {
    const result = await render(ProfileSettingsPage, { providers: providers() });
    const http = result.fixture.debugElement.injector.get(HttpTestingController);
    http.expectOne(PROFILE_URL).flush(PROFILE);
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });

    const displayName = await screen.findByLabelText('Como prefere ser chamado');
    await user.clear(displayName);
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    await settle();

    const request = http.expectOne(PROFILE_URL);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toMatchObject({ fullName: 'Rafael Mendes' });
    expect(request.request.body.displayName).toBeUndefined();
    request.flush(PROFILE);

    expect(await screen.findByText('Alterações salvas.')).toBeVisible();
  });
});

/** P27 — preferências. */
describe('PreferencesSettingsPage', () => {
  it('o tema salvo na conta é aplicado ao carregar, sem esperar salvar', async () => {
    const result = await render(PreferencesSettingsPage, { providers: providers() });
    const http = result.fixture.debugElement.injector.get(HttpTestingController);
    const themeStore = result.fixture.debugElement.injector.get(ThemeStore);

    http.expectOne(PROFILE_URL).flush(PROFILE);
    http.expectOne((request) => request.url === CATEGORIES_URL).flush([]);
    await settle();

    expect(themeStore.preference()).toBe('DARK');
  });

  it('salvar envia as preferências escolhidas', async () => {
    const result = await render(PreferencesSettingsPage, { providers: providers() });
    const http = result.fixture.debugElement.injector.get(HttpTestingController);
    http.expectOne(PROFILE_URL).flush(PROFILE);
    http.expectOne((request) => request.url === CATEGORIES_URL).flush([]);
    await settle();
    result.fixture.detectChanges();

    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    await user.click(await screen.findByRole('button', { name: 'Salvar alterações' }));
    await settle();

    const request = http.expectOne(`${PROFILE_URL}/preferences`);
    expect(request.request.body).toMatchObject({
      theme: 'DARK',
      dashboardPeriod: 'LAST_7_DAYS',
      timerReminderEnabled: true,
    });
    request.flush(PROFILE);
  });
});

/** P28 — notificações. */
describe('NotificationSettingsPage', () => {
  it('tipo não silenciável aparece desabilitado, com a explicação', async () => {
    const result = await render(NotificationSettingsPage, { providers: providers() });
    const http = result.fixture.debugElement.injector.get(HttpTestingController);

    http.expectOne(NOTIFICATION_PREFS_URL).flush({
      emailNotifications: true,
      mutedNotificationTypes: ['TICKET_ASSIGNED'],
      availableTypes: [
        { type: 'PERIOD_AT_RISK', label: 'Saldo em risco', severity: 'CRITICAL', canMute: false },
        { type: 'TICKET_ASSIGNED', label: 'Ticket atribuído', severity: 'INFO', canMute: true },
      ],
    });

    expect(await screen.findByText('sempre enviada')).toBeVisible();
    expect(screen.getByLabelText('Saldo em risco')).toBeDisabled();
    // A caixa marca "receber": o tipo silenciado aparece desmarcado.
    expect(screen.getByLabelText('Ticket atribuído')).not.toBeChecked();
  });
});

/** P33 — auditoria. */
describe('AuditSettingsPage', () => {
  it('consulta sem datas deixa o padrão de 30 dias para o servidor', async () => {
    const result = await render(AuditSettingsPage, { providers: providers() });
    const http = result.fixture.debugElement.injector.get(HttpTestingController);

    const request = http.expectOne((candidate) => candidate.url === AUDIT_URL);
    expect(request.request.params.has('occurredFrom')).toBe(false);
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
  });

  it('intervalo acima de 90 dias é barrado antes da requisição', async () => {
    const result = await render(AuditSettingsPage, { providers: providers() });
    const http = result.fixture.debugElement.injector.get(HttpTestingController);
    http
      .expectOne((candidate) => candidate.url === AUDIT_URL)
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });

    const page = result.fixture.componentInstance as unknown as {
      from: { set: (value: string) => void };
      to: { set: (value: string) => void };
    };
    page.from.set('2026-01-01');
    page.to.set('2026-12-31');
    result.fixture.detectChanges();

    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    await user.click(screen.getByRole('button', { name: 'Consultar' }));
    await settle();

    expect(await screen.findByText(/intervalo máximo por consulta é de 90 dias/)).toBeVisible();
    http.expectNone((candidate) => candidate.url === AUDIT_URL);
  });
});
