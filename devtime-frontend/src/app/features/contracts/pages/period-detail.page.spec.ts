import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import { axe, toHaveNoViolations } from 'jest-axe';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { AuthStore } from '../../../core/auth/auth.store';
import { balanceFixture } from '../../../shared/models/balance.fixture';
import { PeriodDetailPage } from './period-detail.page';

expect.extend(toHaveNoViolations);

const PERIOD_ID = '018f2b4c-0000-7000-8000-000000000001';
const CONTRACT_ID = '018f2b4c-0000-7000-8000-000000000002';

/**
 * P16 — teste de integração com API simulada (FR-182) e verificação de acessibilidade (FR-183).
 *
 * FR-180: todas as consultas são por papel, rótulo ou texto. Nenhum seletor de CSS.
 */
describe('PeriodDetailPage', () => {
  async function setup(permissions: readonly string[] = ['PERIOD_VIEW']) {
    const result = await render(PeriodDetailPage, {
      inputs: { id: CONTRACT_ID, periodId: PERIOD_ID },
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
      ],
    });

    const http = result.fixture.debugElement.injector.get(HttpTestingController);
    const authStore = result.fixture.debugElement.injector.get(AuthStore);
    authStore.applySession({
      accessToken: 'token',
      user: { id: 'u1', fullName: 'Rafael Mendes', displayName: null, email: 'r@e.com' },
      tenant: { id: 't1', name: 'Acme', slug: 'acme', status: 'ACTIVE' },
      role: 'OWNER',
      permissions: [...permissions],
      tenants: [],
      tenantSelectionRequired: false,
    } as never);

    return { ...result, http };
  }

  /**
   * Deixa as promessas pendentes resolverem.
   *
   * FR-187: a sincronização é por microtarefa, nunca por `setTimeout` — o relógio é falso (FR-185) e
   * um temporizador real tornaria o teste instável.
   */
  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  /**
   * Responde às requisições da carga da página.
   *
   * `balance` e `statement` partem juntas; contrato, períodos e ajustes só são disparados depois que
   * o saldo chega, porque dependem do `contractId` que ele traz.
   */
  async function flushLoad(http: HttpTestingController, status = 'OPEN'): Promise<void> {
    http
      .expectOne(`${environment.apiBaseUrl}/contract-periods/${PERIOD_ID}`)
      .flush(balanceFixture({ status: status as never }));
    http.expectOne(`${environment.apiBaseUrl}/contract-periods/${PERIOD_ID}/statement`).flush({
      periodId: PERIOD_ID,
      balance: balanceFixture(),
      entries: [
        {
          type: 'CONTRACTED',
          referenceId: null,
          date: '2026-07-01',
          description: '40:00 conforme o contrato CT-0001',
          minutes: 2400,
          runningBalanceMinutes: 2400,
        },
        {
          type: 'WORK_LOG',
          referenceId: 'w1',
          date: '2026-07-12',
          description: 'Corrigir cálculo de frete',
          minutes: -120,
          runningBalanceMinutes: 2280,
        },
      ],
    });

    await settle();

    http.expectOne(`${environment.apiBaseUrl}/contracts/${CONTRACT_ID}`).flush({
      id: CONTRACT_ID,
      code: 'CT-0001',
      name: 'Sustentação Mensal',
      client: { id: 'c1', name: 'Acme Corporation' },
      status: 'ACTIVE',
    });
    http.expectOne(`${environment.apiBaseUrl}/contracts/${CONTRACT_ID}/periods`).flush([]);
    http.expectOne(`${environment.apiBaseUrl}/contract-periods/${PERIOD_ID}/adjustments`).flush([]);
    await settle();
  }

  it('exibe o saldo e o extrato do período', async () => {
    const { http } = await setup();
    await flushLoad(http);

    expect(await screen.findByRole('heading', { name: '2026-07', level: 1 })).toBeVisible();
    expect(screen.getByText(/Sustentação Mensal/)).toBeVisible();
    expect(screen.getByText('Corrigir cálculo de frete')).toBeVisible();
  });

  it('RN-702: período aberto é marcado como parcial', async () => {
    const { http } = await setup();
    await flushLoad(http);

    expect(await screen.findAllByText('Parcial')).not.toHaveLength(0);
  });

  it('DT-02: sem PERIOD_ADJUST, a ação de ajustar é ocultada, não desabilitada', async () => {
    const { http } = await setup(['PERIOD_VIEW']);
    await flushLoad(http);

    await screen.findByRole('heading', { name: '2026-07', level: 1 });
    expect(screen.queryByRole('button', { name: 'Ajustar saldo' })).toBeNull();
  });

  it('com PERIOD_ADJUST e período aberto, a ação de ajustar aparece', async () => {
    const { http } = await setup(['PERIOD_VIEW', 'PERIOD_ADJUST']);
    await flushLoad(http);

    expect(await screen.findByRole('button', { name: 'Ajustar saldo' })).toBeVisible();
  });

  it('RN-235: em período fechado a ação de ajustar não é oferecida', async () => {
    const { http } = await setup(['PERIOD_VIEW', 'PERIOD_ADJUST']);
    await flushLoad(http, 'CLOSED');

    await screen.findByRole('heading', { name: '2026-07', level: 1 });
    expect(screen.queryByRole('button', { name: 'Ajustar saldo' })).toBeNull();
  });

  it('RN-242: só período fechado oferece reabertura', async () => {
    const { http } = await setup(['PERIOD_VIEW', 'PERIOD_REOPEN']);
    await flushLoad(http, 'CLOSED');

    expect(await screen.findByRole('button', { name: 'Reabrir' })).toBeVisible();
  });

  it('§8.3: falha de carregamento exibe erro com ação de nova tentativa', async () => {
    const { http } = await setup();
    const problem = {
      code: 'DEVTIME-2002',
      status: 404,
      title: 'Não encontrado',
      detail: 'x',
      traceId: 't',
    };
    http
      .expectOne(`${environment.apiBaseUrl}/contract-periods/${PERIOD_ID}`)
      .flush(problem, { status: 404, statusText: 'Not Found' });
    http
      .expectOne(`${environment.apiBaseUrl}/contract-periods/${PERIOD_ID}/statement`)
      .flush(problem, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(
      await screen.findByRole('heading', { name: 'Não foi possível carregar o período' }),
    ).toBeVisible();
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
  });

  it('FR-140: zero violações do axe-core', async () => {
    const { http, container } = await setup(['PERIOD_VIEW', 'PERIOD_ADJUST', 'PERIOD_CLOSE']);
    await flushLoad(http);
    await screen.findByRole('heading', { name: '2026-07', level: 1 });

    // O axe-core agenda trabalho com `setTimeout`; com o relógio falso do setup global ele nunca
    // completaria. O relógio real vale apenas nesta asserção, que não depende de tempo.
    jest.useRealTimers();
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
