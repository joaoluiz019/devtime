import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/** Fatia da distribuição. */
export interface DonutSlice {
  readonly id: string;
  readonly label: string;
  readonly value: number;
  readonly color?: string;
}

const RADIUS = 45;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * Distribuição em rosca — `dt-donut-chart`.
 *
 * Desenhada com um círculo por fatia e `stroke-dasharray`, sem biblioteca: é a mesma razão do gráfico
 * de barras — o pacote inicial não comporta uma dependência de gráficos para duas visualizações.
 *
 * A legenda é a interface real: cada item é um botão, então filtrar por uma fatia funciona com
 * teclado. Clicar no anel também funciona, mas nunca é o único caminho (A11Y).
 */
@Component({
  selector: 'dt-donut-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() === 0) {
      <p class="dt-donut__empty" i18n="@@chart.empty">Sem dados no período selecionado.</p>
    } @else {
      <div class="dt-donut">
        <svg class="dt-donut__svg" viewBox="0 0 100 100" role="img" [attr.aria-label]="ariaLabel()">
          @for (arc of arcs(); track arc.id) {
            <circle
              class="dt-donut__arc"
              cx="50"
              cy="50"
              [attr.r]="radius"
              [attr.stroke]="arc.color"
              [attr.stroke-dasharray]="arc.dashArray"
              [attr.stroke-dashoffset]="arc.dashOffset"
            />
          }
        </svg>

        <ul class="dt-donut__legend" role="list">
          @for (arc of arcs(); track arc.id) {
            <li>
              <button type="button" class="dt-donut__item" (click)="selected.emit(arc.id)">
                <span class="dt-donut__swatch" [style.background-color]="arc.color"></span>
                <span class="dt-donut__label">{{ arc.label }}</span>
                <span class="dt-donut__value">{{ arc.percentage }}%</span>
              </button>
            </li>
          }
        </ul>
      </div>
    }
  `,
  styles: `
    .dt-donut {
      display: flex;
      align-items: center;
      gap: var(--dt-space-4);
    }

    .dt-donut__svg {
      width: 8rem;
      height: 8rem;
      transform: rotate(-90deg);
    }

    .dt-donut__arc {
      fill: none;
      stroke-width: 10;
    }

    .dt-donut__legend {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: var(--dt-space-1);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .dt-donut__item {
      display: flex;
      align-items: center;
      gap: var(--dt-space-2);
      width: 100%;
      padding: 0;
      border: 0;
      background: none;
      color: var(--dt-text-primary);
      font-size: var(--dt-text-xs);
      text-align: left;
      cursor: pointer;
    }

    .dt-donut__swatch {
      width: 10px;
      height: 10px;
      border-radius: 2px;
    }

    .dt-donut__label {
      flex: 1;
    }

    .dt-donut__value {
      color: var(--dt-text-secondary);
      font-variant-numeric: tabular-nums;
    }

    .dt-donut__empty {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }
  `,
})
export class DonutChartComponent {
  readonly slices = input.required<readonly DonutSlice[]>();
  readonly ariaLabel = input.required<string>();

  readonly selected = output<string>();

  protected readonly radius = RADIUS;

  protected readonly total = computed(() =>
    this.slices().reduce((sum, slice) => sum + slice.value, 0),
  );

  /**
   * Cada fatia é um círculo com traço parcial, deslocado pela soma das anteriores.
   *
   * Paleta de reserva por índice quando o servidor não manda cor: cliente e categoria têm cor
   * própria, mas nem toda série tem — e duas fatias cinzas seriam indistinguíveis.
   */
  protected readonly arcs = computed(() => {
    const total = this.total();
    let consumed = 0;
    return this.slices().map((slice, index) => {
      const fraction = total === 0 ? 0 : slice.value / total;
      const length = fraction * CIRCUMFERENCE;
      const arc = {
        id: slice.id,
        label: slice.label,
        color: slice.color ?? FALLBACK_COLORS[index % FALLBACK_COLORS.length],
        dashArray: `${length} ${CIRCUMFERENCE - length}`,
        dashOffset: -consumed,
        percentage: Math.round(fraction * 100),
      };
      consumed += length;
      return arc;
    });
  });
}

const FALLBACK_COLORS: readonly string[] = [
  '#6366f1',
  '#0ea5e9',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#8b5cf6',
];
