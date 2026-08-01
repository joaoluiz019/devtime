import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PeriodBalance } from '../../../shared/models/balance.model';
import { DurationFormat, DurationPipe } from '../../../shared/pipes/duration.pipe';

/** Uma parcela da composição do saldo. */
interface BreakdownLine {
  readonly key: string;
  readonly label: string;
  readonly minutes: number;
  readonly isTotal: boolean;
  /** Parcelas mostram o sinal; o subtotal é um valor absoluto e não o carrega. */
  readonly format: DurationFormat;
}

/**
 * Composição do saldo: contratado + transportado + ajustes − consumido (T-011-15).
 *
 * MV-02: um saldo que o cliente não consegue conferir é tão ruim quanto um saldo errado. Este
 * componente mostra a **aritmética**, não só o resultado.
 *
 * Os valores vêm prontos do servidor. O único cálculo local é a soma exibida na linha de total, que
 * repete o que a API já entregou em `availableMinutes` — se as duas divergirem, o número exibido é o
 * do servidor, porque a fórmula canônica é dele (FR-045, RP-03).
 *
 * BS-01 / EX-02: linhas de valor zero são exibidas, nunca ocultadas. Um ajuste de zero minutos não
 * existe (RN-235), mas um período sem transporte é comum — e omitir a linha faria o usuário procurar
 * por ela.
 */
@Component({
  selector: 'dt-balance-breakdown',
  imports: [DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- BS-04 / A11Y-12: tabela real, com cabeçalhos associados por scope. -->
    <table class="dt-breakdown">
      <caption class="dt-visually-hidden" i18n="@@breakdown.caption">
        Composição do saldo do período
      </caption>
      <thead>
        <tr>
          <th scope="col" i18n="@@breakdown.column.component">Componente</th>
          <th scope="col" class="dt-breakdown__value" i18n="@@breakdown.column.amount">Horas</th>
        </tr>
      </thead>
      <tbody>
        @for (line of lines(); track line.key) {
          <tr [class.dt-breakdown__row--total]="line.isTotal">
            <th scope="row">{{ line.label }}</th>
            <td class="dt-breakdown__value dt-duration">
              {{ line.minutes | duration: line.format }}
            </td>
          </tr>
        }
      </tbody>
      <tfoot>
        <tr class="dt-breakdown__row--balance">
          <th scope="row">{{ balanceLabel() }}</th>
          <td class="dt-breakdown__value dt-duration" [class.dt-severity-critical]="isOverage()">
            {{ balanceDisplay() | duration }}
          </td>
        </tr>
      </tfoot>
    </table>
  `,
  styleUrl: './balance-breakdown.component.scss',
})
export class BalanceBreakdownComponent {
  readonly balance = input.required<PeriodBalance>();

  protected readonly lines = computed<readonly BreakdownLine[]>(() => {
    const balance = this.balance();
    return [
      {
        key: 'contracted',
        label: $localize`:@@breakdown.contracted:Horas contratadas`,
        minutes: balance.contractedMinutes,
        isTotal: false,
        format: 'signed',
      },
      {
        key: 'carriedIn',
        label: $localize`:@@breakdown.carriedIn:Transportado do período anterior`,
        minutes: balance.carriedInMinutes,
        isTotal: false,
        format: 'signed',
      },
      {
        key: 'adjustment',
        label: $localize`:@@breakdown.adjustment:Ajustes manuais`,
        minutes: balance.adjustmentMinutes,
        isTotal: false,
        format: 'signed',
      },
      {
        key: 'available',
        label: $localize`:@@breakdown.available:Total disponível`,
        minutes: balance.availableMinutes,
        isTotal: true,
        format: 'plain',
      },
      {
        key: 'consumed',
        label: $localize`:@@breakdown.consumed:Horas consumidas (faturáveis)`,
        minutes: -balance.consumedMinutes,
        isTotal: false,
        format: 'signed',
      },
    ];
  });

  protected readonly isOverage = computed(() => this.balance().remainingMinutes < 0);

  protected readonly balanceLabel = computed(() =>
    this.isOverage()
      ? $localize`:@@breakdown.overage:Excedente`
      : $localize`:@@breakdown.balance:Saldo`,
  );

  protected readonly balanceDisplay = computed(() =>
    this.isOverage() ? this.balance().overageMinutes : this.balance().remainingMinutes,
  );
}
