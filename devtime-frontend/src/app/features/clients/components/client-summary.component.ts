import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { ClientSummary } from '../data/client.model';

/**
 * Consumo consolidado do cliente — `dt-client-summary` (T-003-22).
 *
 * Responde "quanto este cliente contratou e quanto já usou", somando os contratos do período
 * corrente. O detalhamento por contrato aparece junto porque o total sozinho não diz **onde** as
 * horas foram: um cliente com três contratos e um deles estourado tem total confortável e um
 * problema real.
 *
 * Excedente ganha destaque próprio: é a informação que muda uma cobrança.
 */
@Component({
  selector: 'dt-client-summary',
  imports: [RouterLink, DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dt-client-summary">
      <h2 class="dt-client-summary__title" i18n="@@client.summary.title">Consumo consolidado</h2>

      @if (summary() === null) {
        <p class="dt-client-summary__empty" i18n="@@client.summary.empty">
          Este cliente ainda não tem contratos com horas registradas.
        </p>
      } @else {
        <dl class="dt-client-summary__totals">
          <dt i18n="@@contract.contracted">Contratado</dt>
          <dd>{{ summary()!.totals.contractedMinutes | duration }}</dd>
          <dt i18n="@@contract.consumed">Consumido</dt>
          <dd>{{ summary()!.totals.consumedMinutes | duration }}</dd>
          <dt i18n="@@contract.remaining">Saldo</dt>
          <dd>{{ summary()!.totals.remainingMinutes | duration }}</dd>
          @if (hasOverage()) {
            <dt class="dt-client-summary__overage" i18n="@@contract.overage">Excedente</dt>
            <dd class="dt-client-summary__overage">
              {{ summary()!.totals.overageMinutes | duration }}
            </dd>
          }
        </dl>

        @if (summary()!.byContract.length > 0) {
          <ul class="dt-client-summary__contracts" role="list">
            @for (item of summary()!.byContract; track item.contractId) {
              <li>
                <a [routerLink]="['/contracts', item.contractId]">{{ item.code }}</a>
                <span class="dt-client-summary__contract-name">{{ item.name }}</span>
                <span>{{ item.minutes | duration }}</span>
              </li>
            }
          </ul>
        }
      }
    </section>
  `,
  styles: `
    .dt-client-summary {
      padding: var(--dt-space-4);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background-color: var(--dt-surface-card);
    }

    .dt-client-summary__title {
      margin: 0 0 var(--dt-space-3);
      font-size: var(--dt-text-sm);
      font-weight: 600;
    }

    .dt-client-summary__empty {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-sm);
    }

    .dt-client-summary__totals {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: var(--dt-space-2) var(--dt-space-3);
      margin: 0;
      font-size: var(--dt-text-sm);
    }

    .dt-client-summary__totals dt {
      color: var(--dt-text-secondary);
    }

    .dt-client-summary__totals dd {
      margin: 0;
      font-variant-numeric: tabular-nums;
    }

    /* DS-05: o excedente também é nomeado; a cor é reforço, não a informação. */
    .dt-client-summary__overage {
      color: var(--dt-color-danger);
    }

    .dt-client-summary__contracts {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      margin: var(--dt-space-3) 0 0;
      padding: var(--dt-space-3) 0 0;
      border-top: 1px solid var(--dt-border);
      list-style: none;
      font-size: var(--dt-text-xs);
    }

    .dt-client-summary__contracts li {
      display: grid;
      grid-template-columns: auto 1fr auto;
      gap: var(--dt-space-2);
    }

    .dt-client-summary__contract-name {
      color: var(--dt-text-secondary);
    }
  `,
})
export class ClientSummaryComponent {
  readonly summary = input.required<ClientSummary | null>();

  protected readonly hasOverage = computed(() => (this.summary()?.totals.overageMinutes ?? 0) > 0);
}
