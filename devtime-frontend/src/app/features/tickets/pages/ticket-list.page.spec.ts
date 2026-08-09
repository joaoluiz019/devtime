import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { AuthStore } from '../../../core/auth/auth.store';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { TicketSummary } from '../data/ticket.model';
import { TicketListPage } from './ticket-list.page';

const TICKETS_URL = `${environment.apiBaseUrl}/tickets`;

const TICKET: TicketSummary = {
  id: '018f2b4c-0000-7000-8000-000000000021',
  key: 'CT-0001-42',
  title: 'Corrigir cálculo de frete',
  type: 'BUG',
  status: 'IN_PROGRESS',
  priority: 'HIGH',
  contractCode: 'CT-0001',
  spentMinutes: 180,
  estimatedMinutes: 120,
  isOverEstimate: true,
  tags: [],
  updatedAt: '2026-08-01T10:00:00Z',
};

/** P17 — lista de tickets (T-007-28). */
describe('TicketListPage', () => {
  async function setup(permissions: readonly string[] = ['TICKET_VIEW', 'TICKET_CREATE']) {
    const result = await render(TicketListPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: TicketListPage }]),
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

  function flush(http: HttpTestingController, content: readonly TicketSummary[] = [TICKET]): void {
    http
      .expectOne((request) => request.url === TICKETS_URL)
      .flush({
        content,
        page: 0,
        size: 20,
        totalElements: content.length,
        totalPages: 1,
        last: true,
      });
  }

  it('consulta ordenando pela atualização mais recente', async () => {
    const { http } = await setup();

    const request = http.expectOne((candidate) => candidate.url === TICKETS_URL);
    expect(request.request.params.get('sort')).toBe('updatedAt,desc');
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
  });

  it('RN-309: o ticket acima da estimativa é marcado com texto, não só com cor', async () => {
    const { http } = await setup();
    flush(http);

    expect(await screen.findByText('acima da estimativa')).toBeVisible();
  });

  it('LS-03: filtro de múltipla escolha viaja como parâmetro repetido', async () => {
    const { http, router, fixture } = await setup();
    flush(http);

    await router.navigate([], { queryParams: { status: ['TODO', 'IN_PROGRESS'] } });
    await settle(fixture);

    const request = http.expectOne((candidate) => candidate.url === TICKETS_URL);
    expect(request.request.params.getAll('status')).toEqual(['TODO', 'IN_PROGRESS']);
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
  });

  it('"meus tickets" filtra pelo usuário da sessão', async () => {
    const { http, user, fixture, router } = await setup();
    flush(http);

    await user.click(await screen.findByRole('button', { name: 'Meus tickets' }));
    await settle(fixture);

    expect(router.url).toContain('assigneeId=u1');
    const request = http.expectOne((candidate) => candidate.url === TICKETS_URL);
    expect(request.request.params.get('assigneeId')).toBe('u1');
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
  });

  it('FR-083: sem TICKET_CREATE a ação de criar é ocultada', async () => {
    const { http } = await setup(['TICKET_VIEW']);
    flush(http);

    expect(screen.queryByRole('button', { name: 'Novo ticket' })).not.toBeInTheDocument();
  });
});
