import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PeriodBalance } from '../../models/balance.model';
import { DurationPipe } from '../../pipes/duration.pipe';
import { ConsumptionGaugeComponent } from '../consumption-gauge/consumption-gauge.component';
import { PartialBadgeComponent } from '../partial-badge/partial-badge.component';

/**
 * Cartão de saldo do período: disponível, consumido, restante e taxa (T-011-15).
 *
 * Componente **compartilhado**, reutilizado por `010-dashboard` (T-010-14). É a materialização de
 * DS-02 e DS-03: o saldo aparece sem interação, e nenhum dos quatro números fica atrás de um clique.
 *
 * O selo de parcial é renderizado **aqui**, e não por quem consome o componente, porque RN-702 exige
 * a marcação em toda exibição de período aberto ou reaberto — deixá-la a cargo do chamador garante
 * que uma tela vai esquecer.
 */
@Component({
  selector: 'dt-balance-summary',
  imports: [DurationPipe, ConsumptionGaugeComponent, PartialBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dt-balance-summary" [attr.aria-label]="ariaLabel()">
      <header class="dt-balance-summary__header">
        <h3 class="dt-balance-summary__label">{{ balance().label }}</h3>
        <dt-partial-badge [status]="balance().status" [reopenCount]="balance().reopenCount" />
      </header>

      <!-- BB-06 / CE-CO-03: sem horas disponíveis não há barra; só o total consumido faz sentido. -->
      @if (hasAvailable()) {
        <dt-consumption-gauge [rate]="balance().consumptionRate" />
      }

      <dl class="dt-balance-summary__figures">
        <div class="dt-balance-summary__figure">
          <dt i18n="@@balance.available">Disponível</dt>
          <dd class="dt-duration">{{ balance().availableMinutes | duration }}</dd>
        </div>

        <div class="dt-balance-summary__figure">
          <dt i18n="@@balance.consumed">Consumido</dt>
          <dd class="dt-duration">{{ balance().consumedMinutes | duration }}</dd>
        </div>

        <div class="dt-balance-summary__figure">
          <dt>{{ remainingLabel() }}</dt>
          <dd class="dt-duration" [class.dt-severity-critical]="isOverage()">
            {{ remainingDisplay() | duration }}
          </dd>
        </div>

        <!-- EX-02 / BS-01: linha de valor zero é exibida, nunca ocultada. -->
        <div class="dt-balance-summary__figure">
          <dt i18n="@@balance.nonBillable">Não faturáveis</dt>
          <dd class="dt-duration dt-balance-summary__muted">
            {{ balance().nonBillableMinutes | duration }}
          </dd>
        </div>
      </dl>
    </section>
  `,
  styleUrl: './balance-summary.component.scss',
})
export class BalanceSummaryComponent {
  readonly balance = input.required<PeriodBalance>();

  protected readonly hasAvailable = computed(() => this.balance().availableMinutes > 0);

  protected readonly isOverage = computed(() => this.balance().remainingMinutes < 0);

  /**
   * O excedente é rotulado como excedente, nunca como "restante negativo" (§10 do design system).
   * "Restam −02:20" é uma frase que o usuário precisa traduzir; "excedente 02:20" já é a informação.
   */
  protected readonly remainingLabel = computed(() =>
    this.isOverage()
      ? $localize`:@@balance.overage:Excedente`
      : $localize`:@@balance.remaining:Restante`,
  );

  /** No excedente exibimos `overageMinutes`, que o servidor já entrega positivo. */
  protected readonly remainingDisplay = computed(() =>
    this.isOverage() ? this.balance().overageMinutes : this.balance().remainingMinutes,
  );

  protected readonly ariaLabel = computed(
    () => $localize`:@@balance.aria:Saldo do período ${this.balance().label}:label:`,
  );
}
