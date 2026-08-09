import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { TicketStatus } from '../../../shared/components/ticket-badges/ticket-badges.component';
import { TicketApi } from './ticket.api';
import { TicketBoardColumn } from './ticket.model';

/** Ordem das colunas do quadro; é o fluxo de trabalho, não a ordem alfabética do enum. */
const COLUMN_ORDER: readonly TicketStatus[] = [
  'BACKLOG',
  'TODO',
  'IN_PROGRESS',
  'BLOCKED',
  'IN_REVIEW',
  'DONE',
];

/**
 * Estado do quadro de tickets (P18, T-007-29).
 *
 * O servidor devolve as colunas que existem; o store garante que **todas** apareçam, na ordem do
 * fluxo. Uma coluna ausente porque está vazia faria o quadro mudar de forma conforme o trabalho anda,
 * e não haveria onde soltar um cartão para a situação sem cartões.
 */
@Injectable()
export class TicketBoardStore {
  private readonly api = inject(TicketApi);

  private readonly _columns = signal<readonly TicketBoardColumn[]>([]);
  private readonly _loading = signal(false);
  private readonly _moving = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly loading = this._loading.asReadonly();
  readonly moving = this._moving.asReadonly();
  readonly error = this._error.asReadonly();

  readonly columns = computed<readonly TicketBoardColumn[]>(() => {
    const received = this._columns();
    return COLUMN_ORDER.map(
      (status) =>
        received.find((column) => column.status === status) ?? {
          status,
          totalCount: 0,
          totalSpentMinutes: 0,
          tickets: [],
        },
    );
  });

  readonly totalTickets = computed(() =>
    this.columns().reduce((total, column) => total + column.totalCount, 0),
  );

  /** O quadro traz até 50 cartões por coluna; acima disso a tela precisa dizer que há mais. */
  hasHiddenTickets(column: TicketBoardColumn): boolean {
    return column.totalCount > column.tickets.length;
  }

  async load(filters: { contractId?: string; assigneeId?: string } = {}): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const board = await firstValueFrom(this.api.board(filters));
      this._columns.set(board.columns);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._columns.set([]);
    } finally {
      this._loading.set(false);
    }
  }

  /**
   * Move um cartão de coluna.
   *
   * **Sem atualização otimista.** A transição pode ser recusada por regra de negócio — timer ativo
   * impede `DONE` (RN-311), `BLOCKED` exige motivo (RN-308) —, e um cartão que pula de coluna e volta
   * é pior do que um cartão que espera: quem viu o salto acredita que a mudança valeu.
   *
   * A `version` é buscada aqui porque a projeção do quadro não a traz e RN-004 a exige. É uma
   * requisição a mais por movimento, e não por cartão exibido: buscar todas antecipadamente custaria
   * uma chamada por cartão para usar no máximo uma.
   */
  async move(
    ticketId: string,
    targetStatus: TicketStatus,
    blockReason?: string,
    filters: { contractId?: string; assigneeId?: string } = {},
  ): Promise<boolean> {
    this._moving.set(true);
    this._error.set(null);
    try {
      const ticket = await firstValueFrom(this.api.getById(ticketId));
      await firstValueFrom(
        this.api.transition(ticketId, { targetStatus, blockReason, version: ticket.version }),
      );
      await this.load(filters);
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    } finally {
      this._moving.set(false);
    }
  }
}
