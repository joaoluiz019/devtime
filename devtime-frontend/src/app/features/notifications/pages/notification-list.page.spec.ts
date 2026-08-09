import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { AuthStore } from '../../../core/auth/auth.store';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { NotificationStore } from '../../../core/notifications/notification.store';
import { AppNotification } from '../data/notification.model';
import { NotificationListPage } from './notification-list.page';

const NOTIFICATIONS_URL = `${environment.apiBaseUrl}/notifications`;
const UNREAD_URL = `${NOTIFICATIONS_URL}/unread-count`;

const UNREAD: AppNotification = {
  id: 'n-1',
  type: 'PERIOD_AT_RISK',
  severity: 'CRITICAL',
  title: 'CT-0001 perto do limite',
  body: 'O contrato consumiu 96% do saldo do período.',
  action: { label: 'Abrir contrato', route: '/contracts/c-1' },
  createdAt: '2026-08-04T12:00:00Z',
};

const READ: AppNotification = {
  ...UNREAD,
  id: 'n-2',
  title: 'Período fechado',
  severity: 'INFO',
  readAt: '2026-08-04T13:00:00Z',
};

/** P25 — central de notificações. */
describe('NotificationListPage', () => {
  async function setup() {
    const result = await render(NotificationListPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: NotificationListPage }]),
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
      tenant: {
        id: 't1',
        name: 'Acme',
        slug: 'acme',
        timezone: 'America/Sao_Paulo',
        currency: 'BRL',
        logoUrl: null,
      },
      role: 'OWNER',
      permissions: ['NOTIFICATION_VIEW'],
    });

    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
      store: result.fixture.debugElement.injector.get(NotificationStore),
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  function flushList(
    http: HttpTestingController,
    content: readonly AppNotification[] = [UNREAD, READ],
  ): void {
    http
      .expectOne((request) => request.url === NOTIFICATIONS_URL)
      .flush({
        content,
        page: 0,
        size: 20,
        totalElements: content.length,
        totalPages: 1,
        last: true,
      });
  }

  /** A contagem é recarregada após cada operação; responder mantém a fila limpa. */
  function flushUnread(http: HttpTestingController, unreadCount = 1): void {
    for (const pending of http.match(UNREAD_URL)) {
      pending.flush({ unreadCount, bySeverity: { CRITICAL: unreadCount } });
    }
  }

  it('lista as notificações e distingue lidas de não lidas', async () => {
    const { http } = await setup();
    flushList(http);
    await settle();
    flushUnread(http);

    expect(await screen.findByText('CT-0001 perto do limite')).toBeVisible();
    expect(screen.getByText('não lida')).toBeVisible();
    // A ação sugerida vira link para a rota que o servidor indicou — uma por notificação.
    expect(screen.getAllByRole('link', { name: 'Abrir contrato' })).toHaveLength(2);
  });

  it('marcar como lida atualiza a contagem devolvida pelo servidor', async () => {
    const { http, user, store } = await setup();
    flushList(http);
    await settle();
    flushUnread(http, 1);

    await user.click(await screen.findByRole('button', { name: 'Marcar como lida' }));
    await settle();

    http.expectOne(`${NOTIFICATIONS_URL}/n-1/read`).flush({
      id: 'n-1',
      readAt: '2026-08-04T14:00:00Z',
      unreadCount: 0,
    });
    await settle();
    flushList(http, [{ ...UNREAD, readAt: '2026-08-04T14:00:00Z' }, READ]);
    await settle();
    flushUnread(http, 0);

    expect(store.unreadCount()).toBe(0);
  });

  it('permite desmarcar: a leitura aqui é reversível', async () => {
    const { http, user } = await setup();
    flushList(http, [READ]);
    await settle();
    flushUnread(http, 0);

    await user.click(await screen.findByRole('button', { name: 'Marcar como não lida' }));
    await settle();

    const request = http.expectOne(`${NOTIFICATIONS_URL}/n-2/unread`);
    expect(request.request.method).toBe('POST');
    request.flush({ id: 'n-2', unreadCount: 1 });
    await settle();
    flushList(http, [{ ...READ, readAt: undefined }]);
    await settle();
    flushUnread(http, 1);
  });

  it('filtrar por não lidas pede read=false ao servidor', async () => {
    const { http, user } = await setup();
    flushList(http);
    await settle();
    flushUnread(http);

    await user.click(await screen.findByRole('button', { name: 'Não lidas' }));
    await settle();

    const request = http.expectOne((candidate) => candidate.url === NOTIFICATIONS_URL);
    expect(request.request.params.get('read')).toBe('false');
    request.flush({
      content: [UNREAD],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });
    await settle();
    flushUnread(http);
  });

  it('estado vazio explica o que apareceria ali', async () => {
    const { http } = await setup();
    flushList(http, []);
    await settle();
    flushUnread(http, 0);

    expect(await screen.findByRole('heading', { name: 'Nada por aqui' })).toBeVisible();
  });
});
