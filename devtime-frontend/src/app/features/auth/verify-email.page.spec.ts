import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { VerifyEmailPage } from './verify-email.page';

const VERIFY_URL = `${environment.apiBaseUrl}/auth/verify-email`;
const RESEND_URL = `${environment.apiBaseUrl}/auth/resend-verification`;

/** Verificação de e-mail (P03), incluindo o caminho de link expirado (RN-457). */
describe('VerifyEmailPage', () => {
  async function setup(token: string | null) {
    const result = await render(VerifyEmailPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(token === null ? {} : { token }),
            },
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

  it('verifica sozinha com o token do link', async () => {
    const { http } = await setup('token-do-email');

    const request = http.expectOne(VERIFY_URL);
    expect(request.request.body).toEqual({ token: 'token-do-email' });
    // FR-067: o cookie de refresh emitido junto exige credenciais na requisição.
    expect(request.request.withCredentials).toBe(true);
    request.flush({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresIn: 900,
      tenantSelectionRequired: false,
      user: { id: 'u1', fullName: 'Rafael', displayName: null, email: 'r@e.com', avatarUrl: null },
    });

    expect(await screen.findByRole('heading', { name: 'E-mail verificado' })).toBeVisible();
  });

  it('link expirado oferece o reenvio, não deixa a pessoa sem saída', async () => {
    const { http, user } = await setup('token-velho');

    http.expectOne(VERIFY_URL).flush(
      {
        type: 'about:blank',
        title: 'Expirado',
        status: 410,
        code: 'DEVTIME-1009',
        detail: 'token expirado',
        traceId: 'abc',
      },
      { status: 410, statusText: 'Gone' },
    );

    expect(
      await screen.findByRole('heading', { name: 'Não foi possível verificar' }),
    ).toBeVisible();
    expect(
      screen.getByText('Link expirado. Solicite um novo e-mail de verificação.'),
    ).toBeVisible();

    await user.type(screen.getByLabelText('E-mail cadastrado'), 'rafael@exemplo.com');
    await user.click(screen.getByRole('button', { name: 'Reenviar verificação' }));

    const resend = http.expectOne(RESEND_URL);
    expect(resend.request.body).toEqual({ email: 'rafael@exemplo.com' });
    // SG-01: a mensagem é a mesma exista ou não a conta.
    resend.flush({ message: 'Se o e-mail estiver cadastrado, você receberá as instruções.' });

    expect(
      await screen.findByText('Se o e-mail estiver cadastrado, você receberá as instruções.'),
    ).toBeVisible();
  });

  it('link sem token não chama a API e pede o e-mail', async () => {
    const { http } = await setup(null);

    http.expectNone(VERIFY_URL);
    expect(
      await screen.findByRole('heading', { name: 'Não foi possível verificar' }),
    ).toBeVisible();
    expect(screen.getByLabelText('E-mail cadastrado')).toBeVisible();
  });
});
