import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { ExportStore } from './export.store';
import { ExportExecution } from './report.model';

const EXPORTS_URL = `${environment.apiBaseUrl}/reports/exports`;

const QUEUED: ExportExecution = {
  id: 'exp-1',
  status: 'QUEUED',
  reportType: 'TIMESHEET',
  format: 'XLSX',
  requestedBy: { id: 'u1', name: 'Rafael' },
  parameters: '{"from":"2026-07-01"}',
  progress: null,
  rowCount: null,
  fileName: null,
  sizeBytes: null,
  attemptCount: 0,
  failureReason: null,
  createdAt: '2026-07-29T14:32:10Z',
  completedAt: null,
  expiresAt: null,
};

const COMPLETED: ExportExecution = {
  ...QUEUED,
  status: 'COMPLETED',
  fileName: 'folha.xlsx',
  rowCount: 12_000,
  completedAt: '2026-07-29T14:33:10Z',
};

/** §21.3 — o único polling do produto (T-012-21). */
describe('ExportStore', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ExportStore],
    });
    return {
      store: TestBed.inject(ExportStore),
      http: TestBed.inject(HttpTestingController),
    };
  }

  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  function flushList(http: HttpTestingController, content: readonly ExportExecution[]): void {
    http
      .expectOne((request) => request.url === EXPORTS_URL && request.method === 'GET')
      .flush({
        content,
        page: 0,
        size: 10,
        totalElements: content.length,
        totalPages: 1,
        last: true,
      });
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('acompanha as exportações pendentes a cada 3 segundos', async () => {
    const { store, http } = setup();
    void store.load();
    flushList(http, [QUEUED]);
    await settle();

    expect(store.polling()).toBe(true);

    jest.advanceTimersByTime(3000);
    await settle();

    http.expectOne(`${EXPORTS_URL}/exp-1`).flush({ ...QUEUED, status: 'PROCESSING' });
    await settle();
    expect(store.executions()[0].status).toBe('PROCESSING');
  });

  it('para de acompanhar assim que a exportação conclui', async () => {
    const { store, http } = setup();
    void store.load();
    flushList(http, [QUEUED]);
    await settle();

    jest.advanceTimersByTime(3000);
    await settle();
    http.expectOne(`${EXPORTS_URL}/exp-1`).flush(COMPLETED);
    await settle();

    expect(store.polling()).toBe(false);

    // Nada mais é pedido: perguntar por algo já concluído é trabalho puro de servidor.
    jest.advanceTimersByTime(30_000);
    await settle();
    http.expectNone(`${EXPORTS_URL}/exp-1`);
  });

  it('desiste após 5 minutos: a conclusão passa a chegar por notificação', async () => {
    const { store, http } = setup();
    void store.load();
    flushList(http, [QUEUED]);
    await settle();

    // 100 ciclos de 3 segundos cobrem os 5 minutos; o último deles encontra o limite.
    for (let tick = 0; tick < 100; tick += 1) {
      jest.advanceTimersByTime(3000);
      await settle();
      for (const pending of http.match(`${EXPORTS_URL}/exp-1`)) {
        pending.flush(QUEUED);
      }
      await settle();
    }

    expect(store.polling()).toBe(false);
  });

  it('nenhuma exportação pendente não inicia acompanhamento', async () => {
    const { store, http } = setup();
    void store.load();
    flushList(http, [COMPLETED]);
    await settle();

    expect(store.polling()).toBe(false);
  });
});
