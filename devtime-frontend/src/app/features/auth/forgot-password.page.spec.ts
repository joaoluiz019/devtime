import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { ForgotPasswordPage } from './forgot-password.page';

const FORGOT_URL = `${environment.apiBaseUrl}/auth/forgot-password`;

const CONFIRMATION = /Se o e-mail estiver cadastrado, você receberá as instruções/;

/**
 * Esqueci a senha (P04).
 *
 * O teste central é o de SG-02: sucesso e falha precisam ser **indistinguíveis** na tela, ou a
 * interface volta a permitir a enumeração de e-mails que o backend impede.
 */
describe('ForgotPasswordPage', () => {
  async function setup() {
    const result = await render(ForgotPasswordPage, {
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

  it('FR-103: e-mail inválido é acusado abaixo do campo', async () => {
    const { user, http } = await setup();

    await user.click(screen.getByRole('button', { name: 'Enviar link' }));

    expect(await screen.findByText('Informe um e-mail válido.')).toBeVisible();
    http.expectNone(FORGOT_URL);
  });

  it('envia a solicitação e confirma sem revelar se a conta existe', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('E-mail'), 'rafael@exemplo.com');
    await user.click(screen.getByRole('button', { name: 'Enviar link' }));

    const request = http.expectOne(FORGOT_URL);
    expect(request.request.body).toEqual({ email: 'rafael@exemplo.com' });
    request.flush({ message: 'aceito' }, { status: 202, statusText: 'Accepted' });

    expect(await screen.findByRole('heading', { name: 'Verifique seu e-mail' })).toBeVisible();
    expect(screen.getByText(CONFIRMATION)).toBeVisible();
  });

  it('SG-02: falha do servidor produz exatamente a mesma tela', async () => {
    const { user, http } = await setup();

    await user.type(screen.getByLabelText('E-mail'), 'rafael@exemplo.com');
    await user.click(screen.getByRole('button', { name: 'Enviar link' }));
    http.expectOne(FORGOT_URL).flush(
      {
        type: 'about:blank',
        title: 'Rate limit',
        status: 429,
        code: 'DEVTIME-9002',
        detail: 'muitas tentativas',
        traceId: 'abc',
      },
      { status: 429, statusText: 'Too Many Requests' },
    );

    expect(await screen.findByRole('heading', { name: 'Verifique seu e-mail' })).toBeVisible();
    expect(screen.getByText(CONFIRMATION)).toBeVisible();
  });
});
