import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Client } from '../data/client.model';
import { ClientFormPage } from './client-form.page';

const CLIENTS_URL = `${environment.apiBaseUrl}/clients`;
const CLIENT_ID = '018f2b4c-0000-7000-8000-000000000001';

const EXISTING: Client = {
  id: CLIENT_ID,
  name: 'Acme Corporation',
  legalName: 'Acme Ltda',
  documentType: 'CNPJ',
  documentNumber: '11222333000181',
  email: 'contato@acme.com',
  color: '#4f46e5',
  status: 'ACTIVE',
  activeContractsCount: 1,
  contacts: [],
  createdAt: '2026-07-01T10:00:00Z',
  updatedAt: '2026-07-02T10:00:00Z',
  version: 3,
  availableActions: ['UPDATE', 'DEACTIVATE'],
};

/** P12 — criação e edição (T-003-19). */
describe('ClientFormPage', () => {
  async function setup(id?: string) {
    const result = await render(ClientFormPage, {
      inputs: id === undefined ? {} : { id },
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
      ],
    });
    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  it('FR-104: o botão não é desabilitado por formulário inválido', async () => {
    await setup();

    expect(screen.getByRole('button', { name: 'Criar cliente' })).toBeEnabled();
  });

  it('FM-07: submeter vazio acusa o campo obrigatório e move o foco para ele', async () => {
    const { user, http } = await setup();

    await user.click(screen.getByRole('button', { name: 'Criar cliente' }));

    expect(await screen.findByText('Informe o nome, com 2 a 150 caracteres.')).toBeVisible();
    expect(screen.getByLabelText('Nome *')).toHaveFocus();
    http.expectNone(CLIENTS_URL);
  });

  it('RN-402: documento reprovado nos dígitos não é enviado', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('Nome *'), 'Acme');
    // O tipo nasce em CPF; um CNPJ digitado aí é inválido, como seria no servidor.
    await user.type(screen.getByLabelText('Documento'), '11222333000180');
    await user.click(screen.getByRole('button', { name: 'Criar cliente' }));

    expect(await screen.findByText('Documento inválido: confira os dígitos.')).toBeVisible();
    http.expectNone(CLIENTS_URL);
  });

  it('CX-03: a máscara fica na tela; o que trafega é só dígito', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('Nome *'), 'Acme');
    await user.type(screen.getByLabelText('Documento'), '52998224725');
    await user.click(screen.getByRole('button', { name: 'Criar cliente' }));

    const request = http.expectOne(CLIENTS_URL);
    expect(request.request.body).toMatchObject({
      name: 'Acme',
      documentNumber: '52998224725',
    });
    // Endereço em branco não viaja: o `PUT` substitui o recurso inteiro.
    expect(request.request.body.address).toBeUndefined();
    request.flush(EXISTING);
  });

  it('RN-004: a edição carrega o registro e devolve a version', async () => {
    const { user, http } = await setup(CLIENT_ID);

    http.expectOne(`${CLIENTS_URL}/${CLIENT_ID}`).flush(EXISTING);

    const name = await screen.findByLabelText('Nome *');
    await user.clear(name);
    await user.type(name, 'Acme Brasil');
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    const request = http.expectOne(`${CLIENTS_URL}/${CLIENT_ID}`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ name: 'Acme Brasil', version: 3 });
    request.flush({ ...EXISTING, name: 'Acme Brasil', version: 4 });
  });

  it('FM-06 / RN-404: nome duplicado é acusado no campo do nome', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('Nome *'), 'Acme');
    await user.click(screen.getByRole('button', { name: 'Criar cliente' }));
    http.expectOne(CLIENTS_URL).flush(
      {
        type: 'about:blank',
        title: 'Conflito',
        status: 409,
        code: 'DEVTIME-2404',
        detail: 'nome duplicado',
        traceId: 'abc',
      },
      { status: 409, statusText: 'Conflict' },
    );

    // Duas ocorrências: o resumo do topo e a mensagem do campo (FM-06).
    expect(
      (await screen.findAllByText('Já existe um cliente com este nome.')).length,
    ).toBeGreaterThanOrEqual(2);
  });

  it('FM-08: o formulário sujo é reconhecido pelo guard', async () => {
    const { user, fixture } = await setup();
    const page = fixture.componentInstance;

    expect(page.hasUnsavedChanges()).toBe(false);

    await user.type(screen.getByLabelText('Nome *'), 'Acme');

    expect(page.hasUnsavedChanges()).toBe(true);
  });
});
