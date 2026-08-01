import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { periodStatusIcon, periodStatusLabel } from '../data/period-status';
import { ContractPeriod } from '../data/period.model';

/**
 * Sequência de períodos do contrato, com status e saldo (T-011-33).
 *
 * O banco de horas só faz sentido em série: o transporte de um período é a entrada do seguinte
 * (RN-224 a RN-228). Ver um período isolado esconde de onde veio o saldo inicial — e é justamente
 * essa a pergunta do cliente quando o número não bate.
 *
 * O saldo exibido por período é `contratado + transportado + ajustes − consumido`, os quatro campos
 * que `ContractPeriodResponse` entrega. É o mesmo arranjo do `BalanceCalculator`; a diferença é que
 * aqui ele é apenas **exibição** de valores já calculados, nunca a fonte (CE-F-05).
 */
@Component({
  selector: 'dt-period-timeline',
  imports: [DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="dt-timeline" i18n-aria-label="@@timeline.label" aria-label="Períodos do contrato">
      <ol class="dt-timeline__list">
        @for (period of items(); track period.id) {
          <li>
            <button
              type="button"
              class="dt-timeline__item"
              [class.dt-timeline__item--current]="period.id === selectedId()"
              [attr.aria-current]="period.id === selectedId() ? 'true' : null"
              (click)="selected.emit(period.id)"
            >
              <span class="dt-timeline__label">{{ period.label }}</span>
              <span class="dt-timeline__status">
                <i class="pi" [class]="period.icon" aria-hidden="true"></i>
                {{ period.statusLabel }}
              </span>
              <span
                class="dt-timeline__balance dt-duration"
                [class.dt-severity-critical]="period.remainingMinutes < 0"
              >
                {{ period.remainingMinutes | duration }}
              </span>
            </button>
          </li>
        }
      </ol>
    </nav>
  `,
  styleUrl: './period-timeline.component.scss',
})
export class PeriodTimelineComponent {
  readonly periods = input.required<readonly ContractPeriod[]>();
  readonly selectedId = input.required<string>();

  /** `selected` e não `select`: `select` é evento nativo do DOM (`no-output-native`). */
  readonly selected = output<string>();

  /** FR-026/FR-042: o template não calcula; a derivação vive aqui. */
  protected readonly items = computed(() =>
    this.periods().map((period) => ({
      id: period.id,
      label: period.label,
      statusLabel: periodStatusLabel(period.status),
      icon: periodStatusIcon(period.status),
      remainingMinutes:
        period.contractedMinutes +
        period.carriedInMinutes +
        period.adjustmentMinutes -
        period.consumedMinutes,
    })),
  );
}
