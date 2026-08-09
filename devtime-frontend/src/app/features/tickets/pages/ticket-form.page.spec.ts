import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { TicketFormPage } from './ticket-form.page';

const TICKETS_URL = `${environment.apiBaseUrl}/tickets`;
const CONTRACTS_URL = `${environment.apiBaseUrl}/contracts`;
const MEMBERS_URL = `${environment.apiBaseUrl}/members`;
const TAGS_URL = `${environment.apiBaseUrl}/tags/autocomplete`;

/** P20 — formulário de ticket (T-007-27). */
describe('TicketFormPage', () => {
  async function setup(id?: string) {
    const result = await render(TicketFormPage, {
      inputs: id === undefined ? {} : { id },
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
      ],
    });

    const http = result.fixture.debugElement.injector.get(HttpTestingController);

    // RN-306: o seletor só recebe contratos que aceitam trabalho.
    http
      .expectOne((request) => request.url === CONTRACTS_URL)
      .flush({
        content: [
          {
            id: 'contract-1',
            code: 'CT-0001',
            name: 'Sustentação',
            status: 'ACTIVE',
            client: { id: 'client-1', name: 'Acme' },
          },
          {
            id: 'contract-2',
            code: 'CT-0002',
            name: 'Encerrado',
            status: 'ENDED',
            client: { id: 'client-1', name: 'Acme' },
          },
        ],
        page: 0,
        size: 100,
        totalElements: 2,
        totalPages: 1,
        last: true,
      });
    http
      .expectOne((request) => request.url === MEMBERS_URL)
      .flush({
        content: [],
        page: 0,
        size: 100,
        totalElements: 0,
        totalPages: 0,
        last: true,
      });
    http.expectOne((request) => request.url === TAGS_URL).flush([]);

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

  it('FM-07: submeter vazio acusa contrato e título', async () => {
    const { user, http } = await setup();

    await user.click(screen.getByRole('button', { name: 'Criar ticket' }));

    expect(
      await screen.findByText('Selecione o contrato ao qual o ticket pertence.'),
    ).toBeVisible();
    expect(screen.getByText('Informe um título entre 3 e 200 caracteres.')).toBeVisible();
    http.expectNone(TICKETS_URL);
  });

  it('RN-306: contrato encerrado não aparece no seletor', async () => {
    const { user, fixture } = await setup();

    const page = fixture.componentInstance as unknown as {
      contracts: () => readonly { code: string }[];
    };
    // As três consultas de opção resolvem juntas; sem esperar, a lista ainda está vazia.
    await settle();

    expect(page.contracts().map((contract) => contract.code)).toEqual(['CT-0001']);
    expect(user).toBeDefined();
  });

  it('cria o ticket com o contrato escolhido e mostra a prévia da chave', async () => {
    const { user, http, fixture } = await setup();

    const page = fixture.componentInstance as unknown as {
      form: { controls: { contractId: { setValue: (value: string) => void } } };
    };
    page.form.controls.contractId.setValue('contract-1');
    await settle();
    fixture.detectChanges();

    // A chave é gerada pelo servidor; a tela mostra só o prefixo conhecido.
    expect(await screen.findByText(/A chave será CT-0001-N/)).toBeVisible();

    await user.type(screen.getByLabelText('Título *'), 'Corrigir frete');
    await user.click(screen.getByRole('button', { name: 'Criar ticket' }));

    const request = http.expectOne(TICKETS_URL);
    expect(request.request.body).toMatchObject({
      contractId: 'contract-1',
      title: 'Corrigir frete',
      type: 'FEATURE',
      priority: 'MEDIUM',
    });
    request.flush({ id: 'new-ticket', key: 'CT-0001-43' });
  });

  it('FM-08: o formulário sujo é reconhecido pelo guard', async () => {
    const { user, fixture } = await setup();
    const page = fixture.componentInstance;

    expect(page.hasUnsavedChanges()).toBe(false);
    await user.type(screen.getByLabelText('Título *'), 'Algo');
    expect(page.hasUnsavedChanges()).toBe(true);
  });
});
