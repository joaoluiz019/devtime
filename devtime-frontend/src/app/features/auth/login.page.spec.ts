import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { environment } from '../../../environments/environment';
import { AuthStore } from '../../core/auth/auth.store';
import { LoginPage } from './login.page';

/**
 * Tela de login (P01).
 *
 * FR-180: as consultas são por papel, rótulo e texto — como o usuário enxerga —, nunca por seletor de
 * CSS. Um teste acoplado a `.p-button-primary` quebra na próxima atualização do PrimeNG sem que nada
 * tenha mudado para o usuário.
 */
describe('LoginPage', () => {
  const LOGIN_URL = `${environment.apiBaseUrl}/auth/login`;

  async function setup() {
    const result = await render(LoginPage, {
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
      authStore: result.fixture.debugElement.injector.get(AuthStore),
      // `advanceTimers` é necessário porque o setup global usa temporizadores falsos (FR-185).
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  it('apresenta os campos rotulados e a ação primária', async () => {
    await setup();

    expect(screen.getByRole('heading', { name: 'Entrar' })).toBeVisible();
    // FR-107 / A11Y-04: todo campo possui rótulo associado.
    expect(screen.getByLabelText('E-mail')).toBeVisible();
    expect(screen.getByLabelText('Senha')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeVisible();
  });

  it('FR-104: o botão não é desabilitado por formulário inválido', async () => {
    await setup();

    // Desabilitar esconderia do usuário o que está errado.
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled();
  });

  it('FR-103: erro de campo aparece abaixo do campo, não em toast', async () => {
    const { user } = await setup();

    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('Informe um e-mail válido.')).toBeVisible();
    expect(screen.getByText('Informe sua senha.')).toBeVisible();
  });

  it('FR-105: ao submeter com erros, o foco vai para o primeiro campo inválido', async () => {
    const { user } = await setup();

    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(screen.getByLabelText('E-mail')).toHaveFocus();
  });

  it('envia a credencial quando o formulário está válido', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('E-mail'), 'rafael@exemplo.com');
    await user.type(screen.getByLabelText('Senha'), 'SenhaValida1');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    const request = http.expectOne(LOGIN_URL);
    expect(request.request.body).toEqual({
      email: 'rafael@exemplo.com',
      password: 'SenhaValida1',
    });
    // FR-067: o cookie de refresh exige credenciais na requisição.
    expect(request.request.withCredentials).toBe(true);
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
  });

  it('AU-01: a falha de credencial é exibida com a mensagem mapeada do código', async () => {
    const { authStore } = await setup();

    authStore.setError({
      type: 'about:blank',
      title: 'Autenticação necessária',
      status: 401,
      code: 'DEVTIME-1001',
      detail: 'detalhe técnico do servidor',
      traceId: 'abc123',
    });

    // FR-071 / I18-05: a mensagem vem do mapa localizado, não do texto cru do backend.
    expect(
      await screen.findByText('Sua sessão expirou. Entre novamente para continuar.'),
    ).toBeVisible();
  });
});
