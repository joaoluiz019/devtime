import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';

/**
 * Progresso do ticket contra a estimativa — `dt-ticket-progress` (T-007-28).
 *
 * RN-309: o estouro é **marcado com texto**, não apenas com uma barra vermelha. Um ticket estourado é
 * o que faz o período do contrato estourar depois; quem varre a lista precisa identificá-lo sem
 * depender de percepção de cor (DS-05).
 *
 * Sem estimativa não há progresso: exibir 0% para quem não estimou sugere que nada foi feito. O
 * componente mostra apenas o tempo gasto.
 */
@Component({
  selector: 'dt-ticket-progress',
  imports: [DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-ticket-progress">
      @if (estimatedMinutes(); as estimated) {
        <div class="dt-ticket-progress__track" aria-hidden="true">
          <div
            class="dt-ticket-progress__bar"
            [class.dt-ticket-progress__bar--over]="over()"
            [style.width.%]="width()"
          ></div>
        </div>
        <span class="dt-ticket-progress__label">
          {{ spentMinutes() | duration }} / {{ estimated | duration }}
          @if (over()) {
            <strong class="dt-ticket-progress__over" i18n="@@ticket.overEstimate">
              acima da estimativa
            </strong>
          }
        </span>
      } @else {
        <span class="dt-ticket-progress__label">
          {{ spentMinutes() | duration }}
          <span class="dt-ticket-progress__muted" i18n="@@ticket.noEstimate">sem estimativa</span>
        </span>
      }
    </div>
  `,
  styles: `
    .dt-ticket-progress {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .dt-ticket-progress__track {
      height: 4px;
      border-radius: var(--dt-radius-full);
      background-color: var(--dt-border);
      overflow: hidden;
    }

    .dt-ticket-progress__bar {
      height: 100%;
      background-color: var(--dt-color-primary);
    }

    .dt-ticket-progress__bar--over {
      background-color: var(--dt-color-danger);
    }

    .dt-ticket-progress__label {
      display: flex;
      gap: var(--dt-space-1);
      align-items: baseline;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
      font-variant-numeric: tabular-nums;
    }

    .dt-ticket-progress__over {
      color: var(--dt-color-danger);
    }

    .dt-ticket-progress__muted {
      color: var(--dt-text-disabled);
    }
  `,
})
export class TicketProgressComponent {
  readonly spentMinutes = input.required<number>();
  readonly estimatedMinutes = input<number | undefined>(undefined);
  /** Vem do servidor (RN-309); não é recalculado aqui. */
  readonly isOverEstimate = input<boolean | undefined>(undefined);

  protected readonly over = computed(() => this.isOverEstimate() === true);

  /** A barra satura em 100%: além disso o excesso é comunicado pelo texto, não por transbordo. */
  protected readonly width = computed(() => {
    const estimated = this.estimatedMinutes() ?? 0;
    if (estimated <= 0) {
      return 0;
    }
    return Math.min(100, (this.spentMinutes() / estimated) * 100);
  });
}
