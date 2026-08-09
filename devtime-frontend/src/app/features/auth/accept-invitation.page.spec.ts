import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { InvitationPreview } from '../../core/auth/auth.model';
import { AuthStore } from '../../core/auth/auth.store';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { AcceptInvitationPage } from './accept-invitation.page';

const TOKEN = 'tok-123';
const PEEK_URL = `${environment.apiBaseUrl}/auth/invitations/${TOKEN}`;
const ACCEPT_URL = `${PEEK_URL}/accept`;

const PREVIEW: InvitationPreview = {
  tenantName: 'Acme Software',
  tenantLogoUrl: null,
  invitedByName: 'Camila Torres',
  role: 'MEMBER',
  email: 'diego@exemplo.com',
  userExists: false,
  expiresAt: '2026-08-04T10:00:00Z',
};

/** P07 — aceite de convite (§5.12 de `authentication.md`). */
describe('AcceptInvitationPage', () => {
  async function setup(authenticated = false) {
    const result = await render(AcceptInvitationPage, {
      inputs: { token: TOKEN },
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: AcceptInvitationPage }]),
        MessageService,
      ],
    });

    const authStore = result.fixture.debugElement.injector.get(AuthStore);
    if (authenticated) {
      authStore.applySession({
        accessToken: 'token',
        tokenType: 'Bearer',
        expiresIn: 900,
        tenantSelectionRequired: false,
        user: {
          id: 'u9',
          fullName: 'Rafael Lima',
          displayName: null,
          email: 'rafael@acme.dev',
          avatarUrl: null,
        },
        tenant: {
          id: 't1',
          name: 'Outra Org',
          slug: 'outra',
          timezone: 'America/Sao_Paulo',
          currency: 'BRL',
          logoUrl: null,
        },
        role: 'OWNER',
        permissions: [],
      });
    }

    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  it('conta nova: pede nome e senha, e o aceite aplica a sessão devolvida', async () => {
    const { http, user } = await setup();
    http.expectOne(PEEK_URL).flush(PREVIEW);

    expect(await screen.findByText(/Você foi convidado para Acme Software/)).toBeVisible();
    expect(screen.getByLabelText('Nome completo')).toBeVisible();

    await user.type(screen.getByLabelText('Nome completo'), 'Diego Souza');
    await user.type(screen.getByLabelText('Crie uma senha'), 'Senha#Forte2026');
    await user.click(screen.getByRole('button', { name: 'Aceitar convite' }));
    await settle();

    const request = http.expectOne(ACCEPT_URL);
    expect(request.request.body).toEqual({
      fullName: 'Diego Souza',
      password: 'Senha#Forte2026',
    });
    request.flush({
      accessToken: 'novo',
      tokenType: 'Bearer',
      expiresIn: 900,
      tenantSelectionRequired: false,
      user: {
        id: 'u3',
        fullName: 'Diego Souza',
        displayName: null,
        email: 'diego@exemplo.com',
        avatarUrl: null,
      },
      permissions: [],
    });

    expect(await screen.findByText('Convite aceito')).toBeVisible();
  });

  it('conta existente: pede só a senha', async () => {
    const { http } = await setup();
    http.expectOne(PEEK_URL).flush({ ...PREVIEW, userExists: true });

    expect(await screen.findByLabelText('Sua senha')).toBeVisible();
    expect(screen.queryByLabelText('Nome completo')).toBeNull();
  });

  it('CX-09: quem já tem sessão aceita sem credencial e sem trocar de organização', async () => {
    const { http, user } = await setup(true);
    http.expectOne(PEEK_URL).flush({ ...PREVIEW, userExists: true });

    expect(await screen.findByText(/Sua organização atual continua aberta/)).toBeVisible();
    expect(screen.queryByLabelText('Sua senha')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Aceitar convite' }));
    await settle();

    const request = http.expectOne(ACCEPT_URL);
    expect(request.request.body).toEqual({});
    request.flush({ message: 'Convite aceito.' });

    expect(await screen.findByText('Convite aceito.')).toBeVisible();
  });

  it('avisa quando a sessão em uso é de outro e-mail', async () => {
    const { http } = await setup(true);
    http.expectOne(PEEK_URL).flush({ ...PREVIEW, userExists: true });

    expect(await screen.findByText(/Você está autenticado como rafael@acme.dev/)).toBeVisible();
  });

  it('RN-457: convite expirado é estado final, sem formulário', async () => {
    const { http } = await setup();
    http.expectOne(PEEK_URL).flush(
      {
        type: 'about:blank',
        title: 'Gone',
        status: 410,
        code: 'DEVTIME-2457',
        detail: 'Convite expirado.',
        traceId: 't',
      },
      { status: 410, statusText: 'Gone' },
    );

    expect(await screen.findByText('Convite indisponível')).toBeVisible();
    expect(screen.getByText(/Convite expirado. Solicite um novo./)).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Aceitar convite' })).toBeNull();
  });

  it('DEVTIME-2458: convite revogado também não oferece tentativa', async () => {
    const { http } = await setup();
    http.expectOne(PEEK_URL).flush(
      {
        type: 'about:blank',
        title: 'Not Found',
        status: 404,
        code: 'DEVTIME-2458',
        detail: 'Convite inválido.',
        traceId: 't',
      },
      { status: 404, statusText: 'Not Found' },
    );

    expect(await screen.findByText(/Convite inválido ou já revogado./)).toBeVisible();
  });
});
