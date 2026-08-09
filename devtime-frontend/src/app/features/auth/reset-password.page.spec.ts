import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { ResetPasswordPage } from './reset-password.page';

const RESET_URL = `${environment.apiBaseUrl}/auth/reset-password`;

/** Redefinição de senha (P05): política RN-451, confirmação e token expirado RN-461. */
describe('ResetPasswordPage', () => {
  async function setup(token: string | null = 'token-do-email') {
    const result = await render(ResetPasswordPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(token === null ? {} : { token }) },
          },
        },
      ],
    });
    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  it('link sem token não mostra formulário e aponta o caminho de solicitar outro', async () => {
    await setup(null);

    expect(screen.getByRole('heading', { name: 'Link inválido' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Solicitar novo link' })).toBeVisible();
  });

  it('acusa senhas diferentes sem chamar a API', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('Nova senha'), 'SenhaValida1');
    await user.type(screen.getByLabelText('Repita a nova senha'), 'SenhaValida2');
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));

    expect(await screen.findByText('As senhas não conferem.')).toBeVisible();
    http.expectNone(RESET_URL);
  });

  it('RN-451: senha fora da política não é enviada', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('Nova senha'), 'senhafraca');
    await user.type(screen.getByLabelText('Repita a nova senha'), 'senhafraca');
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));

    http.expectNone(RESET_URL);
    expect(screen.getByText('Uma letra maiúscula')).toBeVisible();
  });

  it('CE-AU-05: o sucesso avisa que as sessões foram encerradas', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('Nova senha'), 'SenhaValida1');
    await user.type(screen.getByLabelText('Repita a nova senha'), 'SenhaValida1');
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));

    const request = http.expectOne(RESET_URL);
    expect(request.request.body).toEqual({
      token: 'token-do-email',
      newPassword: 'SenhaValida1',
    });
    request.flush({ message: 'Senha redefinida com sucesso.' });

    expect(await screen.findByRole('heading', { name: 'Senha redefinida' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Ir para entrar' })).toBeVisible();
  });

  it('RN-461: token expirado exibe o motivo e o caminho para novo link', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('Nova senha'), 'SenhaValida1');
    await user.type(screen.getByLabelText('Repita a nova senha'), 'SenhaValida1');
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));
    http.expectOne(RESET_URL).flush(
      {
        type: 'about:blank',
        title: 'Expirado',
        status: 410,
        code: 'DEVTIME-1007',
        detail: 'token expirado',
        traceId: 'abc',
      },
      { status: 410, statusText: 'Gone' },
    );

    expect(
      await screen.findByText('Link expirado ou já utilizado. Solicite um novo.'),
    ).toBeVisible();
    expect(screen.getByRole('link', { name: 'Solicitar novo link' })).toBeVisible();
  });
});
