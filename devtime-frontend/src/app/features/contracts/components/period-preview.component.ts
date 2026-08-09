import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { PeriodPreviewItem } from '../data/contract.model';

/**
 * Prévia dos períodos que serão gerados — `dt-period-preview` (T-004-18, FM-09).
 *
 * CA-01: a prévia usa o mesmo algoritmo da ativação, então o que aparece aqui é exatamente o que
 * será gravado. É a única chance de conferir o ciclo de faturamento **antes** de ele existir — um dia
 * de faturamento errado só se manifesta um mês depois, no primeiro fechamento torto.
 *
 * O primeiro período proporcional é marcado: é o que explica por que ele tem menos horas que os
 * demais, pergunta que aparece sempre que a proporcionalidade passa despercebida.
 */
@Component({
  selector: 'dt-period-preview',
  imports: [DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dt-preview">
      <h2 class="dt-preview__title" i18n="@@contract.preview.title">Períodos que serão gerados</h2>

      @if (periods().length === 0) {
        <p class="dt-preview__empty" i18n="@@contract.preview.empty">
          Preencha o tipo, a data de início e o dia de faturamento para ver a projeção.
        </p>
      } @else {
        <ol class="dt-preview__list">
          @for (period of periods(); track period.sequence) {
            <li class="dt-preview__item">
              <span class="dt-preview__label">{{ period.label }}</span>
              <span class="dt-preview__range">{{ period.startDate }} — {{ period.endDate }}</span>
              <span class="dt-preview__minutes">{{ period.contractedMinutes | duration }}</span>
              @if (period.prorated) {
                <span class="dt-preview__prorated" i18n="@@contract.preview.prorated">
                  proporcional
                </span>
              }
            </li>
          }
        </ol>
      }
    </section>
  `,
  styles: `
    .dt-preview {
      padding: var(--dt-space-4);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background-color: var(--dt-surface-card);
    }

    .dt-preview__title {
      margin: 0 0 var(--dt-space-3);
      font-size: var(--dt-text-sm);
      font-weight: 600;
    }

    .dt-preview__empty {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-sm);
    }

    .dt-preview__list {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .dt-preview__item {
      display: grid;
      grid-template-columns: auto 1fr auto;
      gap: var(--dt-space-1) var(--dt-space-2);
      align-items: baseline;
      font-size: var(--dt-text-sm);
    }

    .dt-preview__range {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-preview__minutes {
      font-variant-numeric: tabular-nums;
    }

    .dt-preview__prorated {
      grid-column: 1 / -1;
      color: var(--dt-color-info);
      font-size: var(--dt-text-xs);
    }
  `,
})
export class PeriodPreviewComponent {
  readonly periods = input.required<readonly PeriodPreviewItem[]>();
}
