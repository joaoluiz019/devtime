import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen, within } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { Role } from '../../../core/auth/auth.model';
import { AuthStore } from '../../../core/auth/auth.store';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Member, MemberInvitation } from '../data/member.model';
import { TeamSettingsPage } from './team-settings.page';

const MEMBERS_URL = `${environment.apiBaseUrl}/members`;
const INVITATIONS_URL = `${MEMBERS_URL}/invitations`;

const OWNER: Member = {
  id: 'm-owner',
  user: {
    id: 'u1',
    fullName: 'Camila Torres',
    displayName: 'Camila',
    email: 'camila@acme.dev',
    avatarUrl: null,
  },
  role: 'OWNER',
  status: 'ACTIVE',
  invitedAt: null,
  acceptedAt: '2026-01-15T10:00:00Z',
  // O próprio requisitante: RN-456 impede ação sobre o próprio papel, e o servidor já reflete isso.
  availableActions: [],
  version: 1,
};

const DEVELOPER: Member = {
  id: 'm-dev',
  user: {
    id: 'u2',
    fullName: 'Rafael Lima',
    displayName: null,
    email: 'rafael@acme.dev',
    avatarUrl: null,
  },
  role: 'MEMBER',
  status: 'ACTIVE',
  invitedAt: null,
  acceptedAt: '2026-03-01T10:00:00Z',
  availableActions: ['CHANGE_ROLE', 'SUSPEND', 'REMOVE'],
  version: 4,
};

const INVITATION: MemberInvitation = {
  id: 'inv-1',
  email: 'diego@exemplo.com',
  role: 'MEMBER',
  status: 'INVITED',
  invitedAt: '2026-07-28T14:00:00Z',
  expiresAt: '2026-08-04T14:00:00Z',
};

/** P32 — equipe (T-002-37). */
describe('TeamSettingsPage', () => {
  async function setup(
    permissions: readonly string[] = [
      'MEMBER_VIEW',
      'MEMBER_INVITE',
      'MEMBER_UPDATE_ROLE',
      'MEMBER_SUSPEND',
      'MEMBER_REMOVE',
    ],
    role: Role = 'OWNER',
  ) {
    const result = await render(TeamSettingsPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: TeamSettingsPage }]),
        MessageService,
      ],
    });

    const authStore = result.fixture.debugElement.injector.get(AuthStore);
    authStore.applySession({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresIn: 900,
      tenantSelectionRequired: false,
      user: {
        id: 'u1',
        fullName: 'Camila Torres',
        displayName: 'Camila',
        email: 'camila@acme.dev',
        avatarUrl: null,
      },
      role,
      permissions: [...permissions],
    });

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

  /** Espera a lista renderizar; `findBy` é o que dispara a detecção de mudanças no ambiente zoneless. */
  async function loaded(): Promise<void> {
    await screen.findByText('Rafael Lima');
  }

  function flushLists(
    http: HttpTestingController,
    members: readonly Member[] = [OWNER, DEVELOPER],
    invitations: readonly MemberInvitation[] = [INVITATION],
  ): void {
    http
      .expectOne((request) => request.url === MEMBERS_URL && request.method === 'GET')
      .flush({
        content: members,
        page: 0,
        size: 100,
        totalElements: members.length,
        totalPages: 1,
        last: true,
      });
    http.expectOne(INVITATIONS_URL).flush(invitations);
  }

  it('separa membros de convites pendentes', async () => {
    const { http } = await setup();
    flushLists(http);

    expect(await screen.findByText('Camila')).toBeVisible();
    expect(screen.getByText('Rafael Lima')).toBeVisible();
    expect(screen.getByText('diego@exemplo.com')).toBeVisible();
    expect(screen.getByText('Convites pendentes')).toBeVisible();
  });

  it('RN-455: o último proprietário aparece com o motivo, sem ações', async () => {
    const { http } = await setup();
    flushLists(http);
    await settle();

    expect(
      await screen.findByText(/Último proprietário: a organização precisa de um/),
    ).toBeVisible();
    // O membro comum continua com as suas ações — o bloqueio é do proprietário, não da tela.
    expect(screen.getByRole('button', { name: /Remover/ })).toBeVisible();
  });

  it('ME-06: as ações vêm de availableActions, não de dedução da tela', async () => {
    const { http } = await setup();
    const readOnly: Member = { ...DEVELOPER, availableActions: [] };
    flushLists(http, [OWNER, readOnly]);
    await settle();

    expect(screen.queryByRole('button', { name: /Remover/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Suspender/ })).toBeNull();
  });

  it('FA-08: a alteração de papel leva a versão do vínculo', async () => {
    const { http, user } = await setup();
    flushLists(http);
    await loaded();

    const select = screen.getByRole('combobox', { name: 'Papel' });
    await user.click(select);
    await settle();
    await user.click(await screen.findByText('Gestor'));
    await settle();

    const request = http.expectOne(`${MEMBERS_URL}/m-dev/role`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ role: 'MANAGER', version: 4 });
    request.flush({ ...DEVELOPER, role: 'MANAGER', version: 5 });
    await settle();
    flushLists(http, [OWNER, { ...DEVELOPER, role: 'MANAGER', version: 5 }]);
  });

  it('FA-09: a remoção declara o que permanece e exibe o impacto devolvido', async () => {
    const { http, user } = await setup();
    flushLists(http);
    await loaded();

    await user.click(screen.getByRole('button', { name: /Remover/ }));
    await settle();

    const dialog = within(screen.getByRole('dialog'));
    // RN-458 e RN-460 ditos antes da confirmação, não depois.
    expect(
      dialog.getByText(/As horas registradas, os tickets e os comentários permanecem/),
    ).toBeVisible();
    expect(dialog.getByText(/o tempo dele é descartado/)).toBeVisible();

    await user.click(dialog.getByRole('button', { name: 'Remover da organização' }));
    await settle();

    const request = http.expectOne((candidate) => candidate.url === `${MEMBERS_URL}/m-dev`);
    expect(request.request.method).toBe('DELETE');
    request.flush({
      status: 'REMOVED',
      workLogsPreserved: 342,
      ticketsReassigned: 7,
      reassignedTo: 'u1',
      activeTimerDiscarded: true,
    });
    await settle();
    flushLists(http, [OWNER]);

    expect(await screen.findByText(/342 registro\(s\) de horas preservado\(s\)/)).toBeVisible();
  });

  it('FA-06: o convite leva e-mail e papel escolhidos', async () => {
    const { http, user } = await setup();
    flushLists(http);
    await loaded();

    await user.click(screen.getByRole('button', { name: /Convidar membro/ }));
    await settle();

    const dialog = within(screen.getByRole('dialog'));
    await user.type(dialog.getByLabelText('E-mail'), 'novo@exemplo.com');
    await user.click(dialog.getByRole('button', { name: 'Enviar convite' }));
    await settle();

    const request = http.expectOne(INVITATIONS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'novo@exemplo.com', role: 'MEMBER' });
    request.flush(INVITATION);
    await settle();
    flushLists(http);
  });

  it('RN-457: o reenvio é oferecido com o efeito declarado', async () => {
    const { http, user } = await setup();
    flushLists(http);
    await loaded();

    await user.click(screen.getByRole('button', { name: /Reenviar/ }));
    await settle();

    const request = http.expectOne(`${INVITATIONS_URL}/inv-1/resend`);
    expect(request.request.method).toBe('POST');
    request.flush(INVITATION);
    await settle();
    flushLists(http);
  });

  it('sem MEMBER_INVITE não há convite nem ações sobre convites', async () => {
    const { http } = await setup(['MEMBER_VIEW']);
    flushLists(http);
    await settle();

    expect(screen.queryByRole('button', { name: /Convidar membro/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Reenviar/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Revogar/ })).toBeNull();
  });

  it('nota ¹: ADMIN não encontra o papel de proprietário na lista', async () => {
    const { http, user } = await setup(
      ['MEMBER_VIEW', 'MEMBER_INVITE', 'MEMBER_UPDATE_ROLE'],
      'ADMIN',
    );
    flushLists(http);
    await loaded();

    await user.click(screen.getByRole('combobox', { name: 'Papel' }));
    await settle();

    // As opções são lidas pela marcação do próprio seletor: "Proprietário" também é o papel exibido
    // na linha da pessoa autenticada, e uma busca global encontraria aquele rótulo em vez desta opção.
    const options = Array.from(document.querySelectorAll('.dt-role-selector__option-label')).map(
      (element) => element.textContent?.trim(),
    );
    expect(options).toContain('Gestor');
    expect(options).not.toContain('Proprietário');
  });
});
