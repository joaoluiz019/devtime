import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { axe, toHaveNoViolations } from 'jest-axe';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { RegisterPage } from './register.page';

expect.extend(toHaveNoViolations);

const REGISTER_URL = `${environment.apiBaseUrl}/auth/register`;
const RESEND_URL = `${environment.apiBaseUrl}/auth/resend-verification`;

/**
 * Cadastro (P02).
 *
 * FR-180: as consultas são por papel, rótulo e texto — como o usuário enxerga.
 */
describe('RegisterPage', () => {
  async function setup() {
    const result = await render(RegisterPage, {
      providers: [
        // O interceptor de erro é o que converte a resposta em `ProblemDetail`; sem ele o teste
        // exercitaria um caminho que não existe em produção.
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

  async function fillValidForm(user: ReturnType<typeof userEvent.setup>): Promise<void> {
    await user.type(screen.getByLabelText('Nome completo'), 'Rafael Mendes');
    await user.type(screen.getByLabelText('E-mail'), 'rafael@exemplo.com');
    await user.type(screen.getByLabelText('Senha'), 'SenhaValida1');
    await user.click(screen.getByLabelText(/Li e aceito os termos/));
  }

  it('apresenta os campos rotulados e a ação primária', async () => {
    await setup();

    expect(screen.getByRole('heading', { name: 'Criar conta' })).toBeVisible();
    expect(screen.getByLabelText('Nome completo')).toBeVisible();
    expect(screen.getByLabelText('E-mail')).toBeVisible();
    expect(screen.getByLabelText('Senha')).toBeVisible();
    expect(screen.getByLabelText('Nome da organização (opcional)')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Criar conta' })).toBeVisible();
  });

  it('FR-104: o botão não é desabilitado por formulário inválido', async () => {
    await setup();

    expect(screen.getByRole('button', { name: 'Criar conta' })).toBeEnabled();
  });

  it('FR-103 / FR-105: erros ficam abaixo dos campos e o foco vai para o primeiro inválido', async () => {
    const { user } = await setup();

    await user.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(
      await screen.findByText('Informe seu nome completo, com ao menos 2 caracteres.'),
    ).toBeVisible();
    expect(screen.getByText('É necessário aceitar os termos para continuar.')).toBeVisible();
    expect(screen.getByLabelText('Nome completo')).toHaveFocus();
  });

  it('RN-451: a força da senha lista os requisitos que ainda faltam', async () => {
    const { user } = await setup();

    await user.type(screen.getByLabelText('Senha'), 'senha');

    expect(screen.getByText('Ao menos 10 caracteres')).toBeVisible();
    expect(screen.getByText('Uma letra maiúscula')).toBeVisible();
    expect(screen.getByText('Um número')).toBeVisible();
    expect(screen.getByText('fraca')).toBeVisible();
  });

  it('envia o cadastro com o fuso detectado e omite a organização em branco', async () => {
    const { user, http } = await setup();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));

    const request = http.expectOne(REGISTER_URL);
    expect(request.request.body).toEqual({
      email: 'rafael@exemplo.com',
      password: 'SenhaValida1',
      fullName: 'Rafael Mendes',
      tenantName: undefined,
      timezone: expect.any(String),
      acceptedTerms: true,
    });
    request.flush({}, { status: 500, statusText: 'Server Error' });
  });

  it('CP-08: o sucesso leva ao aviso de verificação, não ao produto', async () => {
    const { user, http } = await setup();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));
    http.expectOne(REGISTER_URL).flush({
      userId: 'u1',
      tenantId: 't1',
      email: 'rafael@exemplo.com',
      status: 'PENDING_ACTIVATION',
      verificationEmailSent: true,
    });

    expect(await screen.findByRole('heading', { name: 'Confirme seu e-mail' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Reenviar e-mail' })).toBeVisible();
  });

  it('reenvia a verificação com o e-mail cadastrado', async () => {
    const { user, http } = await setup();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));
    http.expectOne(REGISTER_URL).flush({
      userId: 'u1',
      tenantId: 't1',
      email: 'rafael@exemplo.com',
      status: 'PENDING_ACTIVATION',
      verificationEmailSent: true,
    });
    await user.click(await screen.findByRole('button', { name: 'Reenviar e-mail' }));

    const request = http.expectOne(RESEND_URL);
    expect(request.request.body).toEqual({ email: 'rafael@exemplo.com' });
    request.flush({ message: 'Instruções enviadas.' });

    expect(await screen.findByText('Instruções enviadas.')).toBeVisible();
  });

  it('FM-06 / RN-452: e-mail já cadastrado é acusado no campo, não em toast', async () => {
    const { user, http } = await setup();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));
    http.expectOne(REGISTER_URL).flush(
      {
        type: 'about:blank',
        title: 'Conflito',
        status: 409,
        code: 'DEVTIME-2452',
        detail: 'e-mail em uso',
        traceId: 'abc',
      },
      { status: 409, statusText: 'Conflict' },
    );

    // Duas ocorrências: o resumo do topo e a mensagem do próprio campo (FM-06).
    const messages = await screen.findAllByText('Este e-mail já está em uso.');
    expect(messages.length).toBeGreaterThanOrEqual(2);
  });

  it('FR-140: zero violações do axe-core', async () => {
    const { container } = await setup();

    jest.useRealTimers();
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
