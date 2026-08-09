import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TagModule } from 'primeng/tag';
import { ConsumptionGaugeComponent } from '../../../shared/components/consumption-gauge/consumption-gauge.component';
import { PartialBadgeComponent } from '../../../shared/components/partial-badge/partial-badge.component';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { ContractStatus } from '../data/dashboard.model';

/**
 * Cartão de contrato do painel — `dt-contract-card` (T-010-15).
 *
 * Responde, em um olhar: quanto resta, quantos dias faltam e se o ritmo atual estoura o contrato. A
 * projeção é o que transforma o painel em aviso e não em relatório — saber que vai estourar no dia 12
 * permite agir; saber que estourou não.
 *
 * RN-702: período aberto exibe o selo de parcial. Sem ele, um saldo em evolução é lido como final.
 */
@Component({
  selector: 'dt-contract-card',
  imports: [RouterLink, ConsumptionGaugeComponent, DurationPipe, PartialBadgeComponent, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="dt-contract-card" [class]="'dt-contract-card--' + contract().severity">
      <header class="dt-contract-card__header">
        <div>
          <a class="dt-contract-card__code" [routerLink]="['/contracts', contract().contractId]">
            {{ contract().code }}
          </a>
          <p class="dt-contract-card__client">{{ contract().clientName }}</p>
        </div>
        <dt-partial-badge [status]="contract().isPartial ? 'OPEN' : 'CLOSED'" />
      </header>

      <dt-consumption-gauge [rate]="contract().consumptionRate" />

      <dl class="dt-contract-card__facts">
        <dt i18n="@@dashboard.card.remaining">Saldo</dt>
        <dd>{{ contract().remainingMinutes | duration }}</dd>
        <dt i18n="@@dashboard.card.days">Dias restantes</dt>
        <dd>{{ contract().daysRemaining }}</dd>
      </dl>

      <!-- DS-05: a projeção é texto com selo; a cor apenas reforça. -->
      <p-tag [value]="projectionLabel()" [severity]="projectionSeverity()" />
    </article>
  `,
  styles: `
    .dt-contract-card {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-left-width: 4px;
      border-radius: var(--dt-radius-md);
      background-color: var(--dt-surface-card);
    }

    .dt-contract-card--CRITICAL {
      border-left-color: var(--dt-color-danger);
    }

    .dt-contract-card--WARNING {
      border-left-color: var(--dt-color-warning);
    }

    .dt-contract-card--INFO {
      border-left-color: var(--dt-color-info);
    }

    .dt-contract-card--OK {
      border-left-color: var(--dt-color-success);
    }

    .dt-contract-card__header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--dt-space-2);
    }

    .dt-contract-card__code {
      color: var(--dt-color-primary);
      font-family: var(--dt-font-mono, monospace);
      font-size: var(--dt-text-sm);
    }

    .dt-contract-card__client {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-contract-card__facts {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 2px var(--dt-space-2);
      margin: 0;
      font-size: var(--dt-text-xs);
    }

    .dt-contract-card__facts dt {
      color: var(--dt-text-secondary);
    }

    .dt-contract-card__facts dd {
      margin: 0;
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class ContractCardComponent {
  readonly contract = input.required<ContractStatus>();

  protected readonly projectionLabel = computed(() => {
    switch (this.contract().projectionStatus) {
      case 'WILL_EXCEED':
        return $localize`:@@dashboard.projection.willExceed:Vai estourar no ritmo atual`;
      case 'AT_RISK':
        return $localize`:@@dashboard.projection.atRisk:Em risco de estourar`;
      case 'WITHIN_LIMIT':
        return $localize`:@@dashboard.projection.within:Dentro do contratado`;
      default:
        return $localize`:@@dashboard.projection.na:Sem projeção`;
    }
  });

  protected readonly projectionSeverity = computed(() => {
    switch (this.contract().projectionStatus) {
      case 'WILL_EXCEED':
        return 'danger';
      case 'AT_RISK':
        return 'warn';
      case 'WITHIN_LIMIT':
        return 'success';
      default:
        return 'secondary';
    }
  });
}
