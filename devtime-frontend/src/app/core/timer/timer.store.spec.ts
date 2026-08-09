import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { AuthStore } from '../auth/auth.store';
import { errorInterceptor } from '../http/error.interceptor';
import { FIXED_NOW } from '../../../../setup-jest';
import { Timer } from './timer.model';
import { TimerStore } from './timer.store';

const TIMERS_URL = `${environment.apiBaseUrl}/timers`;

/** Iniciado 10 minutos antes do instante fixo dos testes, sem pausas. */
const RUNNING: Timer = {
  id: 't-1',
  status: 'RUNNING',
  ticket: { id: 'tk-1', key: 'CT-0001-42', title: 'Ajuste de relatório' },
  category: null,
  startedAt: new Date(FIXED_NOW.getTime() - 600_000).toISOString(),
  lastResumedAt: null,
  accumulatedActiveSeconds: 0,
  pausedMinutes: 0,
  billable: true,
  description: null,
  stoppedAt: null,
  workLogId: null,
  availableTransitions: ['PAUSE', 'STOP', 'DISCARD', 'UPDATE'],
  version: 1,
};

/** Pausado com 25 minutos acumulados; o relógio local não pode acrescentar nada a isso. */
const PAUSED: Timer = {
  ...RUNNING,
  status: 'PAUSED',
  accumulatedActiveSeconds: 1500,
  lastResumedAt: new Date(FIXED_NOW.getTime() - 300_000).toISOString(),
  availableTransitions: ['RESUME', 'STOP', 'DISCARD', 'UPDATE'],
};

/** T-009 §21.3 — o servidor é a fonte do estado; o relógio é local. */
describe('TimerStore', () => {
  function setup(permissions: readonly string[] = ['TIMER_USE']) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
      ],
    });
    const authStore = TestBed.inject(AuthStore);
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
      permissions: [...permissions],
    });
    return { store: TestBed.inject(TimerStore), http: TestBed.inject(HttpTestingController) };
  }

  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  afterEach(() => {
    TestBed.inject(TimerStore).disconnect();
  });

  it('RN-151: o tempo decorrido é derivado do estado, não contado pelo cliente', async () => {
    const { store, http } = setup();
    void store.connect();
    await settle();
    http.expectOne(`${TIMERS_URL}/current`).flush(RUNNING);
    await settle();

    expect(store.elapsed()).toBe(600);

    // Um minuto de relógio local, nenhuma requisição: o número anda sozinho entre ressincronizações.
    jest.advanceTimersByTime(30_000);
    await settle();
    expect(store.elapsed()).toBe(630);
  });

  it('pausado, o tempo congela no acumulado do servidor', async () => {
    const { store, http } = setup();
    void store.connect();
    await settle();
    http.expectOne(`${TIMERS_URL}/current`).flush(PAUSED);
    await settle();

    expect(store.elapsed()).toBe(1500);

    jest.advanceTimersByTime(30_000);
    await settle();
    // Somar o intervalo desde `lastResumedAt` contaria a pausa como trabalho.
    expect(store.elapsed()).toBe(1500);
  });

  it('TB-02: ressincroniza a cada 60 segundos, nunca a cada segundo', async () => {
    const { store, http } = setup();
    void store.connect();
    await settle();
    http.expectOne(`${TIMERS_URL}/current`).flush(RUNNING);
    await settle();

    jest.advanceTimersByTime(5_000);
    await settle();
    http.expectNone(`${TIMERS_URL}/current`);

    jest.advanceTimersByTime(55_000);
    await settle();
    http.expectOne(`${TIMERS_URL}/current`).flush(RUNNING);
  });

  it('204 do servidor significa nenhum cronômetro ativo', async () => {
    const { store, http } = setup();
    void store.connect();
    await settle();
    http.expectOne(`${TIMERS_URL}/current`).flush(null, { status: 204, statusText: 'No Content' });
    await settle();

    expect(store.current()).toBeNull();
    expect(store.isActive()).toBe(false);
  });

  it('RN-160: falha ao encerrar mantém o cronômetro ativo e expõe o motivo', async () => {
    const { store, http } = setup();
    void store.connect();
    await settle();
    http.expectOne(`${TIMERS_URL}/current`).flush(RUNNING);
    await settle();

    const stopped = store.stop('Trabalho concluído');
    await settle();
    http.expectOne(`${TIMERS_URL}/current/stop`).flush(
      {
        type: 'about:blank',
        title: 'Unprocessable',
        status: 422,
        code: 'DEVTIME-2102',
        detail: 'Sobreposição.',
        traceId: 't',
      },
      { status: 422, statusText: 'Unprocessable Content' },
    );

    expect(await stopped).toBeNull();
    expect(store.isActive()).toBe(true);
    expect(store.error()?.code).toBe('DEVTIME-2102');
  });

  it('encerrar com sucesso limpa o cronômetro e devolve o registro gerado', async () => {
    const { store, http } = setup();
    void store.connect();
    await settle();
    http.expectOne(`${TIMERS_URL}/current`).flush(RUNNING);
    await settle();

    const stopped = store.stop('Trabalho concluído');
    await settle();
    http.expectOne(`${TIMERS_URL}/current/stop`).flush({
      timer: { ...RUNNING, status: 'COMPLETED' },
      workLog: { id: 'wl-1', netMinutes: 10, durationLabel: '00:10' },
      balance: null,
      warnings: [],
    });

    const result = await stopped;
    expect(result?.workLog.durationLabel).toBe('00:10');
    expect(store.current()).toBeNull();
  });

  it('RN-162: o descarte exige confirmação explícita na chamada', async () => {
    const { store, http } = setup();
    void store.connect();
    await settle();
    http.expectOne(`${TIMERS_URL}/current`).flush(RUNNING);
    await settle();

    void store.discard();
    await settle();

    const request = http.expectOne(
      (candidate) => candidate.url === `${TIMERS_URL}/current` && candidate.method === 'DELETE',
    );
    expect(request.request.params.get('confirm')).toBe('true');
    request.flush(null);
  });

  it('sem TIMER_USE nada é consultado: o papel não registra horas', async () => {
    const { store, http } = setup(['WORKLOG_VIEW_OWN']);
    void store.connect();
    await settle();

    http.expectNone(`${TIMERS_URL}/current`);
    expect(store.current()).toBeNull();
  });
});
