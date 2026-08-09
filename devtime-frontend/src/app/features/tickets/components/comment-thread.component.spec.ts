import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { TicketComment } from '../data/comment.model';
import { CommentThreadComponent } from './comment-thread.component';

const ROOT: TicketComment = {
  id: 'c-1',
  ticketId: 'tk-1',
  body: 'Comecei pelo cálculo do saldo.',
  author: { id: 'u1', name: 'Rafael', handle: 'rafael', avatarUrl: null },
  parentCommentId: null,
  mentionedUsers: [],
  isSystem: false,
  systemTrigger: null,
  createdAt: '2026-07-29T12:00:00Z',
  editedAt: null,
  canEdit: true,
  canDelete: true,
  version: 1,
  replies: [],
};

/** RN-815: o registro automático divide a linha do tempo com as pessoas. */
const SYSTEM: TicketComment = {
  ...ROOT,
  id: 'c-2',
  body: '',
  author: null,
  isSystem: true,
  systemTrigger: 'STATUS_CHANGED',
  canEdit: false,
  canDelete: false,
};

/** RN-812 já expirada: o servidor mandou `canEdit: false`. */
const OLD: TicketComment = {
  ...ROOT,
  id: 'c-3',
  body: 'Comentário antigo.',
  canEdit: false,
  canDelete: false,
};

describe('CommentThreadComponent', () => {
  async function setup(comments: readonly TicketComment[] = [ROOT], canComment = true) {
    const result = await render(CommentThreadComponent, {
      inputs: { comments, total: comments.length, canComment },
    });
    return { ...result, user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }) };
  }

  it('exibe o comentário e oferece responder, editar e excluir conforme o servidor', async () => {
    await setup();

    expect(await screen.findByText('Comecei pelo cálculo do saldo.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Responder' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Editar' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Excluir' })).toBeVisible();
  });

  it('RN-812: sem canEdit do servidor, a tela não oferece edição', async () => {
    await setup([OLD]);

    expect(screen.queryByRole('button', { name: 'Editar' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Excluir' })).toBeNull();
  });

  it('RN-815: o comentário de sistema é descrito, não exibido como fala de alguém', async () => {
    await setup([SYSTEM]);

    expect(await screen.findByText('A situação do ticket mudou.')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Responder' })).toBeNull();
  });

  it('emite o comentário digitado e limpa o campo', async () => {
    const { fixture, user } = await setup();
    const emitted: { body: string; parentCommentId?: string }[] = [];
    fixture.componentInstance.created.subscribe((event) => emitted.push(event));

    await user.type(
      screen.getByLabelText('Escrever um comentário'),
      'Terminei a parte do relatório',
    );
    await user.click(screen.getByRole('button', { name: 'Comentar' }));

    expect(emitted).toEqual([{ body: 'Terminei a parte do relatório' }]);
    expect(screen.getByLabelText<HTMLTextAreaElement>('Escrever um comentário').value).toBe('');
  });

  it('RN-814: a resposta viaja com o comentário raiz como pai', async () => {
    const { fixture, user } = await setup();
    const emitted: { body: string; parentCommentId?: string }[] = [];
    fixture.componentInstance.created.subscribe((event) => emitted.push(event));

    await user.click(screen.getByRole('button', { name: 'Responder' }));
    await user.type(screen.getByLabelText('Responder'), 'Combinado');
    await user.click(screen.getByRole('button', { name: 'Enviar resposta' }));

    expect(emitted[0]?.parentCommentId).toBe('c-1');
  });

  it('sem COMMENT_CREATE não há campo de escrita', async () => {
    await setup([ROOT], false);

    expect(screen.queryByLabelText('Escrever um comentário')).toBeNull();
  });
});
