import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { CommentApi } from './comment.api';
import { TicketComment } from './comment.model';

/**
 * Conversa do ticket (T-014).
 *
 * A paginação é por cursor e **acumula**: a conversa é lida de cima para baixo e trocar de página
 * faria a pessoa perder o contexto do que acabou de ler. "Carregar mais" acrescenta ao fim.
 *
 * Depois de escrever, editar ou excluir, a lista é recarregada do início. Aplicar a resposta na
 * posição local seria mais barato, mas `canEdit` depende de uma janela de 24h avaliada no servidor —
 * e um comentário que expira enquanto a tela está aberta continuaria oferecendo o botão de editar.
 */
@Injectable()
export class CommentStore {
  private readonly api = inject(CommentApi);

  private readonly _comments = signal<readonly TicketComment[]>([]);
  private readonly _cursor = signal<string | null>(null);
  private readonly _hasMore = signal(false);
  private readonly _total = signal(0);
  private readonly _loading = signal(false);
  private readonly _saving = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly comments = this._comments.asReadonly();
  readonly hasMore = this._hasMore.asReadonly();
  readonly total = this._total.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly saving = this._saving.asReadonly();
  readonly error = this._error.asReadonly();

  readonly isEmpty = computed(() => !this._loading() && this._comments().length === 0);

  async load(ticketId: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const thread = await firstValueFrom(this.api.list(ticketId));
      this._comments.set(thread.content);
      this._cursor.set(thread.cursor);
      this._hasMore.set(thread.hasMore);
      this._total.set(thread.totalComments);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
    } finally {
      this._loading.set(false);
    }
  }

  async loadMore(ticketId: string): Promise<void> {
    const cursor = this._cursor();
    if (cursor === null || !this._hasMore()) {
      return;
    }
    this._loading.set(true);
    try {
      const thread = await firstValueFrom(this.api.list(ticketId, cursor));
      this._comments.update((current) => [...current, ...thread.content]);
      this._cursor.set(thread.cursor);
      this._hasMore.set(thread.hasMore);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
    } finally {
      this._loading.set(false);
    }
  }

  /** RN-814: responder a uma resposta vincula à raiz — o servidor normaliza, a tela não precisa. */
  async create(ticketId: string, body: string, parentCommentId?: string): Promise<boolean> {
    return this.mutate(ticketId, () =>
      firstValueFrom(this.api.create(ticketId, { body, parentCommentId })),
    );
  }

  async update(ticketId: string, comment: TicketComment, body: string): Promise<boolean> {
    return this.mutate(ticketId, () =>
      firstValueFrom(this.api.update(comment.id, { body, version: comment.version })),
    );
  }

  async remove(ticketId: string, id: string): Promise<boolean> {
    return this.mutate(ticketId, () => firstValueFrom(this.api.delete(id)));
  }

  private async mutate(ticketId: string, operation: () => Promise<unknown>): Promise<boolean> {
    this._saving.set(true);
    this._error.set(null);
    try {
      await operation();
      await this.load(ticketId);
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    } finally {
      this._saving.set(false);
    }
  }
}
