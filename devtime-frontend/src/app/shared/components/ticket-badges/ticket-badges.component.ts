import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TagModule } from 'primeng/tag';

/** Situações do ticket (`state-machines.md` §4.7). */
export type TicketStatus = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'IN_REVIEW' | 'DONE';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

/**
 * Chave legível do ticket — `dt-ticket-key` (T-007-24).
 *
 * A chave (`CT-0001-42`) é como as pessoas se referem ao ticket em conversa e em commit. Ela é
 * sempre um link: quem lê uma chave quer abrir o ticket.
 */
@Component({
  selector: 'dt-ticket-key',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: ` <a class="dt-ticket-key" [routerLink]="['/tickets', id()]">{{ key() }}</a> `,
  styles: `
    .dt-ticket-key {
      color: var(--dt-color-primary);
      font-family: var(--dt-font-mono, monospace);
      font-size: var(--dt-text-xs);
      white-space: nowrap;
    }
  `,
})
export class TicketKeyComponent {
  readonly id = input.required<string>();
  readonly key = input.required<string>();
}

/**
 * Selo de situação — `dt-ticket-status-badge` (T-007-24).
 *
 * DS-05: situação é texto. `BLOCKED` e `IN_REVIEW` significam coisas opostas para quem decide o que
 * fazer a seguir, e distingui-las por cor exclui quem não a percebe.
 */
@Component({
  selector: 'dt-ticket-status-badge',
  imports: [TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p-tag [value]="label()" [severity]="severity()" />`,
})
export class TicketStatusBadgeComponent {
  readonly status = input.required<TicketStatus>();

  protected readonly label = computed(() => ticketStatusLabel(this.status()));

  protected readonly severity = computed(() => {
    switch (this.status()) {
      case 'DONE':
        return 'success';
      case 'BLOCKED':
        return 'danger';
      case 'IN_PROGRESS':
        return 'info';
      case 'IN_REVIEW':
        return 'warn';
      default:
        return 'secondary';
    }
  });
}

/**
 * Selo de prioridade — `dt-ticket-priority-badge` (T-007-24).
 *
 * Traz ícone além do texto: numa lista longa, a prioridade é lida por varredura, e um ícone dá o
 * relevo que a cor sozinha não pode dar (DS-05).
 */
@Component({
  selector: 'dt-ticket-priority-badge',
  imports: [TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p-tag [value]="label()" [severity]="severity()" [icon]="icon()" />`,
})
export class TicketPriorityBadgeComponent {
  readonly priority = input.required<TicketPriority>();

  protected readonly label = computed(() => ticketPriorityLabel(this.priority()));

  protected readonly severity = computed(() => {
    switch (this.priority()) {
      case 'URGENT':
        return 'danger';
      case 'HIGH':
        return 'warn';
      case 'LOW':
        return 'secondary';
      default:
        return 'info';
    }
  });

  protected readonly icon = computed(() => {
    switch (this.priority()) {
      case 'URGENT':
        return 'pi pi-exclamation-circle';
      case 'HIGH':
        return 'pi pi-angle-double-up';
      case 'LOW':
        return 'pi pi-angle-double-down';
      default:
        return 'pi pi-minus';
    }
  });
}

export function ticketStatusLabel(status: TicketStatus): string {
  switch (status) {
    case 'BACKLOG':
      return $localize`:@@ticket.status.backlog:Backlog`;
    case 'TODO':
      return $localize`:@@ticket.status.todo:A fazer`;
    case 'IN_PROGRESS':
      return $localize`:@@ticket.status.inProgress:Em andamento`;
    case 'BLOCKED':
      return $localize`:@@ticket.status.blocked:Bloqueado`;
    case 'IN_REVIEW':
      return $localize`:@@ticket.status.inReview:Em revisão`;
    default:
      return $localize`:@@ticket.status.done:Concluído`;
  }
}

export function ticketPriorityLabel(priority: TicketPriority): string {
  switch (priority) {
    case 'LOW':
      return $localize`:@@ticket.priority.low:Baixa`;
    case 'MEDIUM':
      return $localize`:@@ticket.priority.medium:Média`;
    case 'HIGH':
      return $localize`:@@ticket.priority.high:Alta`;
    default:
      return $localize`:@@ticket.priority.urgent:Urgente`;
  }
}
