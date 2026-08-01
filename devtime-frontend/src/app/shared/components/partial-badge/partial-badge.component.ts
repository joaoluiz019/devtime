import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { PeriodStatus } from '../../models/balance.model';

/**
 * Selo "parcial" — obrigatório em toda exibição de período aberto ou reaberto (RN-702, T-011-16).
 *
 * **Por que é obrigatório:** um número em evolução exibido sem esta marcação será lido como final, e
 * é assim que um saldo parcial vira uma cobrança contestada (§21.2 de
 * `specs/011-bank-hours/spec.md`).
 *
 * Um período `REOPENED` recebe menção explícita à reabertura: o valor já foi dado como definitivo
 * uma vez, e voltar a mudá-lo sem avisar é pior do que nunca ter fechado.
 */
@Component({
  selector: 'dt-partial-badge',
  imports: [TooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <span class="dt-partial-badge" [pTooltip]="tooltip()" tooltipPosition="top">
        <i class="pi pi-hourglass" aria-hidden="true"></i>
        <span>{{ label() }}</span>
      </span>
    }
  `,
  styles: `
    .dt-partial-badge {
      display: inline-flex;
      gap: var(--dt-space-1);
      align-items: center;
      padding: var(--dt-space-1) var(--dt-space-2);
      border: 1px solid var(--dt-color-info);
      border-radius: var(--dt-radius-full);
      color: var(--dt-color-info);
      font-size: var(--dt-text-xs);
      line-height: var(--dt-text-xs-line);
      white-space: nowrap;
    }

    .dt-partial-badge i {
      font-size: var(--dt-text-xs);
    }
  `,
})
export class PartialBadgeComponent {
  readonly status = input.required<PeriodStatus>();
  readonly reopenCount = input<number>(0);

  protected readonly visible = computed(
    () => this.status() === 'OPEN' || this.status() === 'REOPENED',
  );

  /** DS-05: o selo é texto, nunca apenas a cor da borda. */
  protected readonly label = computed(() =>
    this.status() === 'REOPENED'
      ? $localize`:@@period.partial.reopened:Parcial · reaberto`
      : $localize`:@@period.partial.open:Parcial`,
  );

  protected readonly tooltip = computed(() => {
    if (this.status() === 'REOPENED') {
      return $localize`:@@period.partial.reopened.tooltip:Este período foi reaberto ${this.reopenCount()}:count: vez(es) e os valores voltaram a mudar.`;
    }
    return $localize`:@@period.partial.open.tooltip:O período ainda está aberto: estes valores mudam a cada registro de horas.`;
  });
}
