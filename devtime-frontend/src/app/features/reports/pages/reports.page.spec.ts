import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { AuthStore } from '../../../core/auth/auth.store';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { TimesheetReport } from '../data/report.model';
import { ReportsPage } from './reports.page';

const REPORTS_URL = `${environment.apiBaseUrl}/reports`;
const EXPORTS_URL = `${REPORTS_URL}/exports`;

const TIMESHEET: TimesheetReport = {
  reportType: 'TIMESHEET',
  generatedAt: '2026-07-29T14:32:10Z',
  generatedBy: { id: 'u1', name: 'Rafael' },
  issueId: 'EMI-2026-0001',
  source: 'LIVE',
  isPartial: true,
  groupBy: 'DATE',
  issuer: {
    name: 'Cellentia',
    legalName: 'Cellentia Software ME',
    documentNumber: '12.345.678/0001-90',
    email: 'contato@cellentia.dev',
    phone: null,
    logoUrl: null,
    address: null,
  },
  range: { from: '2026-07-01', to: '2026-07-31' },
  groups: [
    {
      key: '2026-07-15',
      label: '15/07/2026',
      totalNetMinutes: 180,
      totalBillableMinutes: 120,
      durationLabel: '03:00',
      entries: [
        {
          workDate: '2026-07-15',
          startedAt: '2026-07-15T12:00:00Z',
          endedAt: '2026-07-15T14:00:00Z',
          ticketKey: 'CT-0001-42',
          ticketTitle: 'Ajuste de relatório',
          categoryName: 'Desenvolvimento',
          userName: 'Rafael',
          description: 'Implementação',
          netMinutes: 120,
          durationLabel: '02:00',
          decimalHours: 2,
          billable: true,
          tags: [],
          value: null,
        },
        {
          workDate: '2026-07-15',
          startedAt: null,
          endedAt: null,
          ticketKey: 'CT-0001-43',
          ticketTitle: 'Reunião interna',
          categoryName: 'Reunião',
          userName: 'Rafael',
          description: 'Alinhamento',
          netMinutes: 60,
          durationLabel: '01:00',
          decimalHours: 1,
          billable: false,
          tags: [],
          value: null,
        },
      ],
    },
  ],
  summaries: { byCategory: [], byTicket: [], byUser: null },
  totals: {
    entriesCount: 2,
    distinctDays: 1,
    distinctTickets: 2,
    netMinutes: 180,
    billableMinutes: 120,
    nonBillableMinutes: 60,
    durationLabel: '03:00',
    decimalHours: 3,
    totalValue: null,
  },
};

/** P24 — relatórios (T-012-29). */
describe('ReportsPage', () => {
  async function setup(
    permissions: readonly string[] = ['REPORT_VIEW_OWN', 'REPORT_VIEW_ANY', 'REPORT_EXPORT'],
  ) {
    const result = await render(ReportsPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: ReportsPage }]),
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

    const http = result.fixture.debugElement.injector.get(HttpTestingController);
    await settle();
    flushLookups(http);
    await settle();

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

  /** As listas de apoio e as exportações recentes não são objeto de nenhuma asserção aqui. */
  function flushLookups(http: HttpTestingController): void {
    for (const request of http.match(
      (candidate) =>
        candidate.url === `${environment.apiBaseUrl}/categories` ||
        candidate.url === `${environment.apiBaseUrl}/tags/autocomplete`,
    )) {
      request.flush([]);
    }
    for (const request of http.match(
      (candidate) =>
        candidate.url === `${environment.apiBaseUrl}/members` ||
        candidate.url === `${environment.apiBaseUrl}/contracts` ||
        candidate.url === EXPORTS_URL,
    )) {
      request.flush({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        last: true,
      });
    }
  }

  /** Escolhe a folha de horas e preenche o intervalo: é o tipo que não exige alvo nenhum. */
  async function chooseTimesheet(from: string, to: string): Promise<void> {
    fireEvent.click(screen.getByRole('radio', { name: /Folha de horas/ }));
    await settle();

    const fromInput = screen.getByLabelText('De');
    fireEvent.change(fromInput, { target: { value: from } });
    await settle();

    const toInput = screen.getByLabelText('Até');
    fireEvent.change(toInput, { target: { value: to } });
    await settle();
  }

  it('não consulta enquanto o recorte está incompleto', async () => {
    const { http } = await setup();

    jest.advanceTimersByTime(1000);
    await settle();

    http.expectNone((request) => request.url.startsWith(REPORTS_URL));
    expect(screen.getByText(/Complete o recorte acima/)).toBeVisible();
  });

  it('P24: a prévia atualiza com debounce de 500ms', async () => {
    const { http } = await setup();
    await chooseTimesheet('2026-07-01', '2026-07-31');

    // Antes do debounce nenhuma consulta partiu: digitar a data não dispara uma viagem por dígito.
    http.expectNone((request) => request.url === `${REPORTS_URL}/timesheet`);

    jest.advanceTimersByTime(500);
    await settle();

    const request = http.expectOne((candidate) => candidate.url === `${REPORTS_URL}/timesheet`);
    expect(request.request.params.get('from')).toBe('2026-07-01');
    expect(request.request.params.get('to')).toBe('2026-07-31');
    expect(request.request.params.get('groupBy')).toBe('DATE');
    request.flush(TIMESHEET);
  });

  it('RN-702: o aviso de parcial é exibido antes do conteúdo, com o motivo', async () => {
    const { http } = await setup();
    await chooseTimesheet('2026-07-01', '2026-07-31');
    jest.advanceTimersByTime(500);
    await settle();

    http.expectOne((candidate) => candidate.url === `${REPORTS_URL}/timesheet`).flush(TIMESHEET);
    await settle();

    expect(await screen.findByText('Relatório parcial')).toBeVisible();
    expect(screen.getByText(/podem mudar/)).toBeVisible();
  });

  it('RN-705: intervalo acima de 366 dias é recusado sem consultar o servidor', async () => {
    const { http } = await setup();
    await chooseTimesheet('2025-01-01', '2026-12-31');

    jest.advanceTimersByTime(1000);
    await settle();

    expect(screen.getByText(/não pode passar de 366 dias/)).toBeVisible();
    http.expectNone((request) => request.url === `${REPORTS_URL}/timesheet`);
  });

  it('CP-05: a linha não faturável é marcada e fica fora do subtotal faturável', async () => {
    const { http } = await setup();
    await chooseTimesheet('2026-07-01', '2026-07-31');
    jest.advanceTimersByTime(500);
    await settle();

    http.expectOne((candidate) => candidate.url === `${REPORTS_URL}/timesheet`).flush(TIMESHEET);
    await settle();

    expect(await screen.findByText('não faturável')).toBeVisible();
    // O grupo tem 03:00 no total e 02:00 de faturáveis: a linha marcada não entra na segunda soma.
    expect(screen.getAllByText('03:00').length).toBeGreaterThan(0);
    expect(screen.getAllByText('02:00').length).toBeGreaterThan(0);
  });

  it('CX-21: os tipos que exigem REPORT_VIEW_ANY são desabilitados e explicados para MEMBER', async () => {
    await setup(['REPORT_VIEW_OWN', 'REPORT_EXPORT']);

    expect(screen.getByRole('radio', { name: /Resumo por cliente/ })).toBeDisabled();
    expect(screen.getByRole('radio', { name: /Produtividade/ })).toBeDisabled();
    expect(screen.getByRole('radio', { name: /Folha de horas/ })).toBeEnabled();
  });

  it('CE-P-10: MEMBER não recebe o filtro de pessoa', async () => {
    await setup(['REPORT_VIEW_OWN', 'REPORT_EXPORT']);

    expect(screen.queryByLabelText('Pessoas')).toBeNull();
  });

  it('sem REPORT_EXPORT não há botão de exportar nem lista de exportações', async () => {
    await setup(['REPORT_VIEW_OWN']);

    expect(screen.queryByRole('button', { name: 'Exportar' })).toBeNull();
    expect(screen.queryByText('Exportações recentes')).toBeNull();
  });

  it('§8.1: a exportação leva os mesmos parâmetros da consulta', async () => {
    const { http, user } = await setup();
    await chooseTimesheet('2026-07-01', '2026-07-31');
    jest.advanceTimersByTime(500);
    await settle();

    http.expectOne((candidate) => candidate.url === `${REPORTS_URL}/timesheet`).flush(TIMESHEET);
    await settle();

    await user.click(screen.getByRole('button', { name: 'Exportar' }));
    await settle();
    const dialog = within(screen.getByRole('dialog'));
    await user.click(dialog.getByRole('button', { name: 'Exportar' }));
    await settle();

    const request = http.expectOne(EXPORTS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.reportType).toBe('TIMESHEET');
    expect(request.request.body.parameters.filters.from).toBe('2026-07-01');
    expect(request.request.body.parameters.filters.to).toBe('2026-07-31');
    // CE-R-12: duas requisições idênticas precisam ser distinguíveis como a mesma exportação.
    expect(request.request.headers.get('Idempotency-Key')).not.toBeNull();
    request.flush({
      id: 'exp-1',
      status: 'COMPLETED',
      format: 'PDF',
      fileName: 'folha.pdf',
      sizeBytes: 1024,
      rowCount: 2,
      estimatedRowCount: 2,
      downloadUrl: null,
      pollUrl: null,
      expiresAt: null,
      generatedAt: '2026-07-29T14:32:10Z',
      message: null,
    });
  });
});
