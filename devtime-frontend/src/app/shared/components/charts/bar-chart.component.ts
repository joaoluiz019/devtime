import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Ponto de uma série diária. */
export interface BarPoint {
  readonly label: string;
  readonly value: number;
  /** Parte do valor que é faturável; desenhada como faixa mais escura dentro da barra. */
  readonly highlight?: number;
}

/**
 * Gráfico de barras em SVG — `dt-bar-chart`.
 *
 * **Sem biblioteca de gráficos.** O projeto não depende de uma, e uma série de trinta barras não
 * justifica acrescentá-la: `chart.js` sozinho custaria mais que todo o painel, e o pacote inicial já
 * está no limite do orçamento (FR-167).
 *
 * A11Y-13: um gráfico não pode ser a única forma de acessar o dado. A tabela equivalente vai junto,
 * visível para leitor de tela — não é alternativa escondida, é o mesmo conteúdo em outra forma.
 */
@Component({
  selector: 'dt-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (points().length === 0) {
      <p class="dt-chart__empty" i18n="@@chart.empty">Sem dados no período selecionado.</p>
    } @else {
      <figure class="dt-chart">
        <svg
          class="dt-chart__svg"
          [attr.viewBox]="'0 0 ' + width() + ' ' + height"
          role="img"
          [attr.aria-label]="ariaLabel()"
          [attr.preserveAspectRatio]="'none'"
        >
          @for (bar of bars(); track bar.label) {
            <g>
              <rect
                class="dt-chart__bar"
                [attr.x]="bar.x"
                [attr.y]="bar.y"
                [attr.width]="barWidth"
                [attr.height]="bar.height"
              />
              @if (bar.highlightHeight > 0) {
                <rect
                  class="dt-chart__bar dt-chart__bar--highlight"
                  [attr.x]="bar.x"
                  [attr.y]="bar.highlightY"
                  [attr.width]="barWidth"
                  [attr.height]="bar.highlightHeight"
                />
              }
            </g>
          }
        </svg>

        <figcaption class="dt-chart__caption">{{ caption() }}</figcaption>

        <!-- O mesmo dado em tabela: o gráfico é visual, os números são para todos. -->
        <table class="dt-chart__table">
          <caption>
            {{
              ariaLabel()
            }}
          </caption>
          <tbody>
            @for (point of points(); track point.label) {
              <tr>
                <th scope="row">{{ point.label }}</th>
                <td>{{ point.value }}</td>
              </tr>
            }
          </tbody>
        </table>
      </figure>
    }
  `,
  styles: `
    .dt-chart {
      margin: 0;
    }

    .dt-chart__svg {
      width: 100%;
      height: 10rem;
    }

    .dt-chart__bar {
      fill: var(--dt-color-primary);
      opacity: 0.35;
    }

    .dt-chart__bar--highlight {
      opacity: 1;
    }

    .dt-chart__caption,
    .dt-chart__empty {
      margin: var(--dt-space-1) 0 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    /* Visível para leitor de tela, fora do fluxo visual — o gráfico já mostra a forma. */
    .dt-chart__table {
      position: absolute;
      width: 1px;
      height: 1px;
      margin: -1px;
      padding: 0;
      border: 0;
      clip-path: inset(50%);
      overflow: hidden;
      white-space: nowrap;
    }
  `,
})
export class BarChartComponent {
  readonly points = input.required<readonly BarPoint[]>();
  readonly ariaLabel = input.required<string>();
  readonly caption = input('');

  protected readonly height = 100;
  protected readonly barWidth = 8;

  private readonly gap = 4;

  protected readonly width = computed(
    () => Math.max(1, this.points().length) * (this.barWidth + this.gap),
  );

  /**
   * Escala pelo maior valor da série.
   *
   * Uma escala fixa deixaria semanas inteiras rasas demais para comparar; o rótulo do eixo vai na
   * legenda, e o que a barra comunica é a proporção entre os dias.
   */
  private readonly max = computed(() => Math.max(1, ...this.points().map((point) => point.value)));

  protected readonly bars = computed(() =>
    this.points().map((point, index) => {
      const barHeight = (point.value / this.max()) * this.height;
      const highlightHeight = ((point.highlight ?? 0) / this.max()) * this.height;
      return {
        label: point.label,
        x: index * (this.barWidth + this.gap),
        y: this.height - barHeight,
        height: barHeight,
        highlightY: this.height - highlightHeight,
        highlightHeight,
      };
    }),
  );
}
