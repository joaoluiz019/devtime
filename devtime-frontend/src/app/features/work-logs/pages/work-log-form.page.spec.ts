import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { WorkLogFormPage } from './work-log-form.page';

const WORKLOGS_URL = `${environment.apiBaseUrl}/work-logs`;
const VALIDATE_URL = `${WORKLOGS_URL}/validate`;
const TICKETS_URL = `${environment.apiBaseUrl}/tickets`;
const CATEGORIES_URL = `${environment.apiBaseUrl}/categories`;

/** P23 — formulário de registro de horas (T-008-31). */
describe('WorkLogFormPage', () => {
  async function setup(id?: string) {
    const result = await render(WorkLogFormPage, {
      inputs: id === undefined ? {} : { id },
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
      ],
    });

    const http = result.fixture.debugElement.injector.get(HttpTestingController);
    http
      .expectOne((request) => request.url === TICKETS_URL)
      .flush({
        content: [
          {
            id: 'ticket-1',
            key: 'CT-0001-42',
            title: 'Corrigir frete',
            status: 'IN_PROGRESS',
            contractCode: 'CT-0001',
          },
        ],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        last: true,
      });
    // RN-104: só categorias ativas são oferecidas.
    const categories = http.expectOne((request) => request.url === CATEGORIES_URL);
    expect(categories.request.params.get('active')).toBe('true');
    categories.flush([
      { id: 'cat-1', name: 'Desenvolvimento', color: '#4f46e5', billableByDefault: true },
    ]);

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

  function setForm(
    fixture: { componentInstance: unknown; detectChanges: () => void },
    values: Record<string, unknown>,
  ): void {
    const page = fixture.componentInstance as unknown as {
      form: { patchValue: (value: Record<string, unknown>) => void };
    };
    page.form.patchValue(values);
  }

  it('RN-110/111: a duração aparece enquanto se digita, sem esperar o servidor', async () => {
    const { fixture } = await setup();

    setForm(fixture, {
      ticketId: 'ticket-1',
      workDate: '2026-08-05',
      startTime: '09:00',
      endTime: '12:00',
      pausedMinutes: 30,
    });
    await settle();
    fixture.detectChanges();

    // 3h brutas menos 30 min de pausa = 02:30 líquidas, no formato HH:MM do produto.
    expect(await screen.findByText('03:00')).toBeVisible();
    expect(screen.getByText('02:30')).toBeVisible();
  });

  it('sessão que atravessa a meia-noite avisa que o fim é no dia seguinte', async () => {
    const { fixture, http } = await setup();

    setForm(fixture, {
      ticketId: 'ticket-1',
      description: 'Plantão noturno',
      workDate: '2026-08-05',
      startTime: '23:00',
      endTime: '01:00',
    });
    await settle();
    fixture.detectChanges();

    expect(await screen.findByText('O fim é no dia seguinte.')).toBeVisible();

    jest.advanceTimersByTime(500);
    await settle();
    const validate = http.expectOne(VALIDATE_URL);
    // O fim vai para o dia 6; interpretar como mesmo dia produziria duração negativa.
    expect(validate.request.body.endedAt).toContain('2026-08-06');
    validate.flush({ valid: true, errors: [], warnings: [], conflicts: [] });
  });

  it('RN-116: pausa maior que o bruto é acusada e nada é enviado', async () => {
    const { fixture, user, http } = await setup();

    setForm(fixture, {
      ticketId: 'ticket-1',
      description: 'Ajuste no cálculo',
      workDate: '2026-08-05',
      startTime: '09:00',
      endTime: '10:00',
      pausedMinutes: 90,
    });
    await settle();
    fixture.detectChanges();

    await user.click(screen.getByRole('button', { name: 'Registrar horas' }));
    await settle();

    expect(await screen.findByText('A pausa precisa ser menor que a duração bruta.')).toBeVisible();
    http.expectNone(WORKLOGS_URL);
  });

  it('RN-102: o conflito devolvido pela validação aponta o registro sobreposto', async () => {
    const { fixture, http } = await setup();

    setForm(fixture, {
      ticketId: 'ticket-1',
      description: 'Sessão da manhã',
      workDate: '2026-08-05',
      startTime: '09:00',
      endTime: '11:00',
    });
    jest.advanceTimersByTime(500);
    await settle();

    http.expectOne(VALIDATE_URL).flush({
      valid: false,
      errors: [],
      warnings: [],
      conflicts: [
        {
          id: 'other-log',
          workDate: '2026-08-05',
          startedAt: '2026-08-05T12:30:00Z',
          endedAt: '2026-08-05T13:30:00Z',
          ticketKey: 'CT-0001-40',
        },
      ],
    });
    fixture.detectChanges();

    expect(await screen.findByRole('link', { name: 'CT-0001-40' })).toBeVisible();
  });

  it('mostra o efeito no saldo antes de gravar', async () => {
    const { fixture, http } = await setup();

    setForm(fixture, {
      ticketId: 'ticket-1',
      description: 'Sessão longa',
      workDate: '2026-08-05',
      startTime: '09:00',
      endTime: '17:00',
    });
    jest.advanceTimersByTime(500);
    await settle();

    http.expectOne(VALIDATE_URL).flush({
      valid: true,
      errors: [],
      warnings: [
        { code: 'DEVTIME-2220', message: 'Excede o saldo do período.', exceedingMinutes: 60 },
      ],
      conflicts: [],
      balancePreview: {
        contractPeriodId: 'period-1',
        availableMinutes: 2400,
        consumedBeforeMinutes: 2000,
        consumedAfterMinutes: 2480,
        remainingAfterMinutes: -80,
      },
    });
    fixture.detectChanges();

    expect(
      await screen.findByText('Este registro ultrapassa o saldo contratado do período.'),
    ).toBeVisible();
    expect(screen.getByText('Excede o saldo do período.')).toBeVisible();
  });

  it('envia o registro com o intervalo resolvido', async () => {
    const { fixture, user, http } = await setup();

    setForm(fixture, {
      ticketId: 'ticket-1',
      description: 'Ajuste no cálculo de frete',
      workDate: '2026-08-05',
      startTime: '09:00',
      endTime: '11:30',
      pausedMinutes: 0,
      categoryId: 'cat-1',
    });
    jest.advanceTimersByTime(500);
    await settle();
    for (const pending of http.match(VALIDATE_URL)) {
      pending.flush({ valid: true, errors: [], warnings: [], conflicts: [] });
    }
    fixture.detectChanges();

    await user.click(screen.getByRole('button', { name: 'Registrar horas' }));
    await settle();

    const request = http.expectOne(WORKLOGS_URL);
    expect(request.request.body).toMatchObject({
      ticketId: 'ticket-1',
      description: 'Ajuste no cálculo de frete',
      categoryId: 'cat-1',
      billable: true,
    });
    request.flush({
      workLog: { id: 'new-log', workDate: '2026-08-05' },
      warnings: [],
    });
  });
});
