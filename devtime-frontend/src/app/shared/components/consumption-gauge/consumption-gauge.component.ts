import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ConsumptionRatePipe } from '../../pipes/consumption-rate.pipe';
import {
  CRITICALITY_THRESHOLDS,
  criticalityClass,
  criticalityIcon,
  criticalityLabel,
  criticalityOf,
} from '../../utils/criticality';

/**
 * Medidor de consumo com os limiares 50/80/100 e cor por criticidade (T-011-15).
 *
 * Componente **compartilhado**: `010-dashboard` o reutiliza em `dt-contract-status-card` (T-010-14).
 * Recriá-lo lá produziria duas representações visuais do mesmo saldo, que divergiriam (§492 de
 * `specs/010-dashboard/spec.md`).
 *
 * **Justificativa de componente customizado (FR-125):** duas razões, ambas verificadas.
 *
 * 1. `p-progressBar` não marca limiares sobre o trilho nem distingue o excedente (BB-03/BB-04).
 * 2. Na versão 21 ele emite `aria-level="{valor}%"` por host binding — atributo que não existe para
 *    `role="progressbar"` e cujo valor não é um inteiro. O axe-core acusa duas violações por causa
 *    dele (`aria-allowed-attr` e `aria-valid-attr-value`), e o host binding da biblioteca vence
 *    qualquer `[attr.aria-level]="null"` aplicado de fora. Com FR-140 exigindo **zero** violações,
 *    usar o componente da biblioteca aqui é impossível.
 *
 * A marcação própria é uma `div` com `role="progressbar"` e os quatro atributos exigidos por BB-05 —
 * a mesma semântica, sem o atributo inválido.
 */
@Component({
  selector: 'dt-consumption-gauge',
  imports: [ConsumptionRatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-gauge">
      <div class="dt-gauge__track">
        <!-- BB-05: role, valores e rótulo descritivo. -->
        <div
          class="dt-gauge__rail"
          role="progressbar"
          aria-valuemin="0"
          aria-valuemax="100"
          [attr.aria-valuenow]="rate()"
          [attr.aria-label]="ariaLabel()"
        >
          <div class="dt-gauge__fill" [class]="fillClass()" [style.width.%]="displayRate()"></div>
        </div>

        <!-- BB-04: limiares como marcadores sobre o trilho. -->
        <div class="dt-gauge__markers" aria-hidden="true">
          @for (threshold of thresholds(); track threshold) {
            <span class="dt-gauge__marker" [style.left.%]="threshold"></span>
          }
        </div>
      </div>

      <!-- DS-05 / BB-02: ícone e rótulo sempre acompanham a cor. -->
      <p class="dt-gauge__legend" [class]="severityClass()">
        <i class="pi" [class]="icon()" aria-hidden="true"></i>
        <span class="dt-gauge__severity">{{ severity() }}</span>
        <span class="dt-gauge__rate dt-duration">{{ rate() | consumptionRate }}</span>
      </p>
    </div>
  `,
  styleUrl: './consumption-gauge.component.scss',
})
export class ConsumptionGaugeComponent {
  /** Taxa de consumo percentual, como vem do servidor. */
  readonly rate = input.required<number>();

  /** Limiares exibidos como marcadores; o padrão é a tabela normativa §5.3. */
  readonly thresholds = input<readonly number[]>([
    CRITICALITY_THRESHOLDS.info,
    CRITICALITY_THRESHOLDS.warning,
  ]);

  /** BB-03: acima de 100% a barra permanece cheia; o excedente é comunicado por textura e texto. */
  protected readonly displayRate = computed(() => Math.min(Math.max(this.rate(), 0), 100));

  private readonly criticality = computed(() => criticalityOf(this.rate()));

  protected readonly severityClass = computed(() => criticalityClass(this.criticality()));
  protected readonly icon = computed(() => criticalityIcon(this.criticality()));
  protected readonly severity = computed(() => criticalityLabel(this.criticality()));

  protected readonly fillClass = computed(
    () => `dt-gauge__fill--${this.criticality().toLowerCase()}`,
  );

  /** BB-05: rótulo descritivo, não apenas o número. */
  protected readonly ariaLabel = computed(
    () => $localize`:@@gauge.aria:Consumo do período: ${this.severity()}:severity:`,
  );
}
