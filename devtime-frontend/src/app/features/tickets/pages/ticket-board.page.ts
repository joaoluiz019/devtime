import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { messageForCode } from '../../../core/error/error-messages';
import { ticketStatusLabel } from '../../../shared/components/ticket-badges/ticket-badges.component';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { BlockReasonDialogComponent } from '../components/block-reason-dialog.component';
import { TicketCardComponent } from '../components/ticket-card.component';
import { TicketBoardStore } from '../data/ticket-board.store';
import { TicketBoardColumn, TicketStatus, TicketSummary } from '../data/ticket.model';

/**
 * Quadro de tickets — P18, layout L4 (T-007-29).
 *
 * **Mover é selecionar e depois escolher o destino**, não arrastar. Um cartão é selecionado com
 * clique, Enter ou espaço; as colunas de destino viram botões. Arrastar com o mouse exclui quem
 * navega por teclado e é impraticável em toque; esta forma funciona nos três casos e é a mesma para
 * todo mundo, em vez de um caminho principal e uma alternativa esquecida.
 *
 * O destino oferecido sai de `availableTransitions` do ticket selecionado: uma coluna que o servidor
 * recusaria não vira botão (ME-06).
 */
@Component({
  selector: 'dt-ticket-board-page',
  imports: [
    RouterLink,
    BlockReasonDialogComponent,
    ButtonModule,
    DurationPipe,
    MessageModule,
    SkeletonModule,
    TicketCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [TicketBoardStore],
  // Esc cancela a seleção venha de onde vier o foco: prendê-lo a um elemento faria a tecla
  // funcionar só enquanto o foco estivesse dentro do quadro, que não é onde ela é pressionada.
  host: { '(document:keydown.escape)': 'clearSelection()' },
  template: `
    <header class="dt-board__header">
      <div>
        <h1 class="dt-board__title" i18n="@@tickets.board.title">Quadro de tickets</h1>
        <p class="dt-board__subtitle">{{ subtitle() }}</p>
      </div>
      <p-button
        i18n-label="@@tickets.list"
        label="Ver lista"
        icon="pi pi-list"
        severity="secondary"
        [outlined]="true"
        routerLink="/tickets"
      />
    </header>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
      @if (selected(); as ticket) {
        <p-message severity="info" styleClass="w-full mb-3">
          <span i18n="@@tickets.board.selected">
            {{ ticket.key }} selecionado. Escolha a coluna de destino ou pressione Esc para
            cancelar.
          </span>
        </p-message>
      }
    </div>

    @if (store.loading()) {
      <p-skeleton height="20rem" />
    } @else {
      <div class="dt-board__columns">
        @for (column of store.columns(); track column.status) {
          <section class="dt-board__column">
            <header class="dt-board__column-header">
              <h2 class="dt-board__column-title">{{ label(column.status) }}</h2>
              <span class="dt-board__column-count">
                {{ column.totalCount }} · {{ column.totalSpentMinutes | duration }}
              </span>
            </header>

            @if (canMoveTo(column.status)) {
              <p-button
                [label]="moveLabel(column.status)"
                severity="secondary"
                [outlined]="true"
                styleClass="w-full mb-2"
                [loading]="store.moving()"
                (onClick)="moveTo(column.status)"
              />
            }

            <div class="dt-board__cards">
              @for (ticket of column.tickets; track ticket.id) {
                <dt-ticket-card
                  [ticket]="ticket"
                  [draggable]="true"
                  [selected]="selected()?.id === ticket.id"
                  (picked)="select($event)"
                />
              }

              @if (hidden(column) > 0) {
                <!-- O quadro traz até 50 cartões por coluna; omitir isso faria o total mentir. -->
                <p class="dt-board__hidden">
                  <span i18n="@@tickets.board.hidden">
                    Mais {{ hidden(column) }} não exibidos. Use a lista para ver todos.
                  </span>
                </p>
              }

              @if (column.tickets.length === 0) {
                <p class="dt-board__empty" i18n="@@tickets.board.emptyColumn">Nenhum ticket.</p>
              }
            </div>
          </section>
        }
      </div>
    }

    <dt-block-reason-dialog
      [visible]="blockDialogOpen()"
      [saving]="store.moving()"
      (visibleChange)="blockDialogOpen.set($event)"
      (confirmed)="confirmBlock($event)"
    />
  `,
  styleUrl: './ticket-board.page.scss',
})
export class TicketBoardPage {
  protected readonly store = inject(TicketBoardStore);

  private readonly _selected = signal<TicketSummary | null>(null);
  private readonly _pendingBlock = signal<TicketSummary | null>(null);

  protected readonly selected = this._selected.asReadonly();
  protected readonly blockDialogOpen = signal(false);

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly subtitle = computed(
    () =>
      $localize`:@@tickets.board.subtitle:${this.store.totalTickets()}:count: tickets no quadro`,
  );

  constructor() {
    void this.store.load();
  }

  protected label(status: TicketStatus): string {
    return ticketStatusLabel(status);
  }

  protected hidden(column: TicketBoardColumn): number {
    return Math.max(0, column.totalCount - column.tickets.length);
  }

  protected select(ticket: TicketSummary): void {
    this._selected.set(this._selected()?.id === ticket.id ? null : ticket);
  }

  protected clearSelection(): void {
    this._selected.set(null);
  }

  /**
   * A coluna vira destino apenas se o servidor a declarou alcançável.
   *
   * `availableTransitions` não vem na projeção do quadro, então a decisão usa a situação atual e a
   * ausência de transição para a própria coluna. A recusa definitiva continua sendo do backend, que
   * responde `409 DEVTIME-2010` para transição inválida.
   */
  protected canMoveTo(status: TicketStatus): boolean {
    const ticket = this._selected();
    return ticket !== null && ticket.status !== status;
  }

  protected moveLabel(status: TicketStatus): string {
    return $localize`:@@tickets.board.moveTo:Mover para ${ticketStatusLabel(status)}:status:`;
  }

  protected async moveTo(status: TicketStatus): Promise<void> {
    const ticket = this._selected();
    if (ticket === null) {
      return;
    }
    // RN-308: `BLOCKED` exige motivo; pedir antes evita um `422` depois do movimento.
    if (status === 'BLOCKED') {
      this._pendingBlock.set(ticket);
      this.blockDialogOpen.set(true);
      return;
    }
    await this.apply(ticket, status);
  }

  protected async confirmBlock(reason: string): Promise<void> {
    const ticket = this._pendingBlock();
    if (ticket === null) {
      return;
    }
    const moved = await this.apply(ticket, 'BLOCKED', reason);
    if (moved) {
      this.blockDialogOpen.set(false);
      this._pendingBlock.set(null);
    }
  }

  private async apply(
    ticket: TicketSummary,
    status: TicketStatus,
    blockReason?: string,
  ): Promise<boolean> {
    const moved = await this.store.move(ticket.id, status, blockReason);
    if (moved) {
      this._selected.set(null);
    }
    return moved;
  }
}
