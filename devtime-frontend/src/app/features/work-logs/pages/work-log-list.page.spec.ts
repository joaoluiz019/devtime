import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { AuthStore } from '../../../core/auth/auth.store';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { WorkLogSummary } from '../data/work-log.model';
import { WorkLogListPage } from './work-log-list.page';

const WORKLOGS_URL = `${environment.apiBaseUrl}/work-logs`;
const TOTALS_URL = `${WORKLOGS_URL}/totals`;

const ENTRY: WorkLogSummary = {
  id: 'log-1',
  workDate: '2026-08-04',
  startedAt: '2026-08-04T12:00:00Z',
  endedAt: '2026-08-04T14:00:00Z',
  ticketKey: 'CT-0001-42',
  ticketId: 'ticket-1',
  categoryName: 'Desenvolvimento',
  userId: 'u1',
  netMinutes: 120,
  billableMinutes: 120,
  durationLabel: '02:00',
  billable: true,
  source: 'MANUAL',
};

const LOCKED: WorkLogSummary = {
  ...ENTRY,
  id: 'log-2',
  lockedAt: '2026-08-01T00:00:00Z',
};

/** P21 — lista de registros (T-008-33). */
describe('WorkLogListPage', () => {
  async function setup(permissions: readonly string[] = ['WORKLOG_VIEW_OWN', 'WORKLOG_CREATE']) {
    const result = await render(WorkLogListPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: WorkLogListPage }]),
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
      router: result.fixture.debugElement.injector.get(Router),
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

  function flush(http: HttpTestingController, content: readonly WorkLogSummary[] = [ENTRY]): void {
    http
      .expectOne((request) => request.url === WORKLOGS_URL)
      .flush({
        content,
        page: 0,
        size: 20,
        totalElements: content.length,
        totalPages: 1,
        last: true,
      });
    http
      .expectOne((request) => request.url === TOTALS_URL)
      .flush({
        totalMinutes: 300,
        billableMinutes: 240,
        nonBillableMinutes: 60,
        entryCount: 3,
        byCategory: [],
      });
  }

  it('LS-02: os totais vêm do servidor com os mesmos filtros, não da soma da página', async () => {
    const { http } = await setup();
    flush(http);

    // A página tem um registro de 02:00, mas o total do filtro é 05:00.
    expect(await screen.findByText('05:00')).toBeVisible();
    expect(screen.getByText('04:00')).toBeVisible();
    expect(screen.getByText('01:00')).toBeVisible();
  });

  it('os dois pedidos levam o mesmo filtro de data', async () => {
    const { http, router, fixture } = await setup();
    flush(http);

    await router.navigate([], { queryParams: { dateFrom: '2026-08-01', dateTo: '2026-08-31' } });
    await settle(fixture);

    const list = http.expectOne((request) => request.url === WORKLOGS_URL);
    const totals = http.expectOne((request) => request.url === TOTALS_URL);
    expect(list.request.params.get('dateFrom')).toBe('2026-08-01');
    expect(totals.request.params.get('dateFrom')).toBe('2026-08-01');
    list.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
    totals.flush({
      totalMinutes: 0,
      billableMinutes: 0,
      nonBillableMinutes: 0,
      entryCount: 0,
      byCategory: [],
    });
  });

  it('RN-121: registro de período fechado perde as ações e diz por quê', async () => {
    const { http } = await setup();
    flush(http, [LOCKED]);

    expect(await screen.findByText('Período fechado')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Editar registro' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Excluir registro' })).not.toBeInTheDocument();
  });

  it('exclui o registro e recarrega a listagem', async () => {
    const { http, user, fixture } = await setup();
    flush(http);

    await user.click(await screen.findByRole('button', { name: 'Excluir registro' }));
    await settle(fixture);

    http.expectOne(`${WORKLOGS_URL}/log-1`).flush(null);
    await settle(fixture);
    flush(http, []);

    expect(
      await screen.findByRole('heading', { name: 'Nenhum registro encontrado' }),
    ).toBeVisible();
  });

  it('"só os meus" filtra pelo usuário da sessão', async () => {
    const { http, user, fixture, router } = await setup();
    flush(http);

    await user.click(await screen.findByRole('button', { name: 'Só os meus' }));
    await settle(fixture);

    expect(router.url).toContain('userId=u1');
    flush(http);
  });
});
