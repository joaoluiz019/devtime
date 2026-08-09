import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TicketActivityEvent } from '../data/ticket.model';

/**
 * Linha do tempo do ticket — `dt-ticket-timeline` (T-007-31).
 *
 * Une auditoria e comentários em ordem cronológica decrescente, como o backend entrega. A paginação é
 * por cursor e explícita: rolagem infinita numa lista de auditoria faz o rodapé fugir e impede voltar
 * ao ponto onde se estava lendo.
 *
 * Registros de horas de outras pessoas já vêm omitidos pelo servidor para quem não tem
 * `WORKLOG_VIEW_ANY` (§9 de `permissions.md`) — o filtro não é reproduzido aqui.
 */
@Component({
  selector: 'dt-ticket-timeline',
  imports: [DatePipe, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dt-timeline">
      <h2 class="dt-timeline__title" i18n="@@ticket.timeline.title">Atividade</h2>

      @if (events().length === 0) {
        <p class="dt-timeline__empty" i18n="@@ticket.timeline.empty">
          Nenhuma atividade registrada até agora.
        </p>
      } @else {
        <ol class="dt-timeline__list">
          @for (event of events(); track event.occurredAt + event.type) {
            <li class="dt-timeline__item">
              <span class="dt-timeline__when">{{ event.occurredAt | date: 'short' }}</span>
              <span class="dt-timeline__what">
                <strong>{{ event.actor?.name ?? systemLabel }}</strong>
                {{ describe(event) }}
              </span>
            </li>
          }
        </ol>

        @if (hasMore()) {
          <p-button
            i18n-label="@@ticket.timeline.more"
            label="Carregar mais"
            severity="secondary"
            [text]="true"
            [loading]="loading()"
            (onClick)="loadMore.emit()"
          />
        }
      }
    </section>
  `,
  styles: `
    .dt-timeline__title {
      margin: 0 0 var(--dt-space-3);
      font-size: var(--dt-text-lg);
    }

    .dt-timeline__empty {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-sm);
    }

    .dt-timeline__list {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      margin: 0 0 var(--dt-space-3);
      padding: 0;
      list-style: none;
    }

    .dt-timeline__item {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: var(--dt-space-3);
      font-size: var(--dt-text-sm);
    }

    .dt-timeline__when {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
      white-space: nowrap;
    }
  `,
})
export class TicketTimelineComponent {
  readonly events = input.required<readonly TicketActivityEvent[]>();
  readonly hasMore = input(false);
  readonly loading = input(false);

  readonly loadMore = output<void>();

  /** RN-458: eventos automáticos e de usuários removidos precisam de um autor exibível. */
  protected readonly systemLabel = $localize`:@@ticket.timeline.system:Sistema`;

  /**
   * Descrição do evento em linguagem natural.
   *
   * O tipo cru (`STATUS_CHANGED`) não é apresentável, e os tipos desconhecidos não podem sumir da
   * linha do tempo: um evento sem tradução ainda é a prova de que **algo** aconteceu ali.
   */
  protected describe(event: TicketActivityEvent): string {
    switch (event.type) {
      case 'CREATED':
        return $localize`:@@ticket.event.created:criou o ticket`;
      case 'STATUS_CHANGED':
        return $localize`:@@ticket.event.status:mudou a situação`;
      case 'ASSIGNED':
        return $localize`:@@ticket.event.assigned:alterou o responsável`;
      case 'COMMENT':
        return $localize`:@@ticket.event.comment:comentou`;
      case 'WORKLOG':
        return $localize`:@@ticket.event.worklog:registrou horas`;
      case 'CONTRACT_MOVED':
        return $localize`:@@ticket.event.moved:moveu o ticket de contrato`;
      case 'UPDATED':
        return $localize`:@@ticket.event.updated:atualizou o ticket`;
      default:
        return $localize`:@@ticket.event.other:registrou uma alteração`;
    }
  }
}
