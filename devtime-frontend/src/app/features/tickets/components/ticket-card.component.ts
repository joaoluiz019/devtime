import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  TicketPriorityBadgeComponent,
  TicketStatusBadgeComponent,
} from '../../../shared/components/ticket-badges/ticket-badges.component';
import { TicketSummary } from '../data/ticket.model';
import { TicketProgressComponent } from './ticket-progress.component';

/**
 * Cartão de ticket — `dt-ticket-card` (T-007-28).
 *
 * Usado na lista em telas estreitas e como cartão do quadro (P18). No quadro ele é focalizável e
 * responde ao teclado: LD-03 e A11Y exigem que mover um cartão não dependa de arrastar com o mouse.
 */
@Component({
  selector: 'dt-ticket-card',
  imports: [
    RouterLink,
    TicketPriorityBadgeComponent,
    TicketProgressComponent,
    TicketStatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article
      class="dt-ticket-card"
      [class.dt-ticket-card--selected]="selected()"
      [attr.tabindex]="draggable() ? 0 : null"
      [attr.role]="draggable() ? 'button' : null"
      [attr.aria-label]="draggable() ? ticket().key + ' — ' + ticket().title : null"
      [attr.aria-pressed]="draggable() ? selected() : null"
      (keydown)="onKeydown($event)"
      (click)="picked.emit(ticket())"
    >
      <header class="dt-ticket-card__header">
        <a class="dt-ticket-card__key" [routerLink]="['/tickets', ticket().id]">
          {{ ticket().key }}
        </a>
        <dt-ticket-priority-badge [priority]="ticket().priority" />
      </header>

      <a class="dt-ticket-card__title" [routerLink]="['/tickets', ticket().id]">
        {{ ticket().title }}
      </a>

      @if (showStatus()) {
        <dt-ticket-status-badge [status]="ticket().status" />
      }

      <dt-ticket-progress
        [spentMinutes]="ticket().spentMinutes"
        [estimatedMinutes]="ticket().estimatedMinutes"
        [isOverEstimate]="ticket().isOverEstimate"
      />

      <footer class="dt-ticket-card__footer">
        <span class="dt-ticket-card__contract">{{ ticket().contractCode }}</span>
        @if (ticket().assignee; as assignee) {
          <span class="dt-ticket-card__assignee">{{ assignee.name }}</span>
        } @else {
          <span class="dt-ticket-card__assignee" i18n="@@ticket.unassigned">Sem responsável</span>
        }
      </footer>
    </article>
  `,
  styles: `
    .dt-ticket-card {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background-color: var(--dt-surface-card);
    }

    .dt-ticket-card--selected {
      border-color: var(--dt-color-primary);
      box-shadow: 0 0 0 2px var(--dt-color-primary);
    }

    .dt-ticket-card__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--dt-space-2);
    }

    .dt-ticket-card__key {
      color: var(--dt-color-primary);
      font-family: var(--dt-font-mono, monospace);
      font-size: var(--dt-text-xs);
    }

    .dt-ticket-card__title {
      color: var(--dt-text-primary);
      font-size: var(--dt-text-sm);
      font-weight: 500;
      text-decoration: none;
    }

    .dt-ticket-card__footer {
      display: flex;
      justify-content: space-between;
      gap: var(--dt-space-2);
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }
  `,
})
export class TicketCardComponent {
  readonly ticket = input.required<TicketSummary>();
  readonly showStatus = input(false);
  /** No quadro o cartão é acionável; na lista ele é apenas conteúdo. */
  readonly draggable = input(false);
  readonly selected = input(false);

  readonly picked = output<TicketSummary>();

  /**
   * Espaço e Enter selecionam o cartão para mover.
   *
   * T-007-29 exige arrastar e soltar **acessível por teclado**: sem isto, mover um cartão seria
   * impossível para quem não usa mouse, e o quadro deixaria de ser uma forma de trabalhar.
   */
  protected onKeydown(event: KeyboardEvent): void {
    if (!this.draggable()) {
      return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.picked.emit(this.ticket());
    }
  }
}
