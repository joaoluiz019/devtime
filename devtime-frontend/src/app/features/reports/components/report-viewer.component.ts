import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { GroupedReport, ProductivityReport, Report } from '../data/report.model';

/**
 * Detalhamento do relatório — `dt-report-viewer` (T-012-25).
 *
 * **A prévia reflete fielmente o arquivo** (P24 em `pages.md`): as mesmas colunas, os mesmos
 * subtotais, o mesmo total, e as durações no `durationLabel` que o servidor formatou (RN-710). Uma
 * tela que calcula a própria formatação diverge do PDF na primeira borda de arredondamento, e a
 * divergência aparece justamente quando alguém confere um documento contestado.
 *
 * CP-05: os não faturáveis aparecem no detalhamento **marcados**, e ficam fora do subtotal
 * faturável. Some-os sem marcação e a linha de cobrança fica maior que a devida.
 *
 * CP-03 / CP-08: a coluna de valor só existe quando o servidor enviou valores. A decisão é dele,
 * não da tela — o mesmo recorte que omite o número aqui omite no arquivo que sai do sistema.
 */
@Component({
  selector: 'dt-report-viewer',
  imports: [DurationPipe, MoneyPipe, TooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (productivity(); as productivityReport) {
      <!-- IDG-02: valores absolutos em ordem alfabética; nenhuma classificação, nenhum destaque. -->
      <section class="dt-report-viewer__block">
        <h3 class="dt-report-viewer__caption" i18n="@@report.productivity.byUser">Por pessoa</h3>
        <table class="dt-report-viewer__table">
          <thead>
            <tr>
              <th scope="col" i18n="@@report.column.user">Pessoa</th>
              <th scope="col" i18n="@@report.column.duration">Horas</th>
              <th scope="col" i18n="@@report.column.billable">Faturáveis</th>
              <th scope="col" i18n="@@report.column.billableRate">% faturável</th>
              <th scope="col" i18n="@@report.column.perWorkDay">Média por dia útil</th>
            </tr>
          </thead>
          <tbody>
            @for (row of productivityReport.byUser; track row.userName) {
              <tr>
                <td>{{ row.userName }}</td>
                <td class="dt-report-viewer__numeric">{{ row.durationLabel }}</td>
                <td class="dt-report-viewer__numeric">{{ row.billableMinutes | duration }}</td>
                <td class="dt-report-viewer__numeric">{{ row.billableRate }}%</td>
                <td class="dt-report-viewer__numeric">{{ row.minutesPerWorkDay | duration }}</td>
              </tr>
            }
          </tbody>
        </table>
      </section>

      <section class="dt-report-viewer__block">
        <h3 class="dt-report-viewer__caption" i18n="@@report.productivity.byWeek">Por semana</h3>
        <table class="dt-report-viewer__table">
          <thead>
            <tr>
              <th scope="col" i18n="@@report.column.week">Semana</th>
              <th scope="col" i18n="@@report.column.range">Intervalo</th>
              <th scope="col" i18n="@@report.column.duration">Horas</th>
              <th scope="col" i18n="@@report.column.billable">Faturáveis</th>
            </tr>
          </thead>
          <tbody>
            @for (row of productivityReport.byWeek; track row.isoWeek) {
              <tr>
                <td>{{ row.isoWeek }}</td>
                <td>{{ row.weekStart }} — {{ row.weekEnd }}</td>
                <td class="dt-report-viewer__numeric">{{ row.durationLabel }}</td>
                <td class="dt-report-viewer__numeric">{{ row.billableMinutes | duration }}</td>
              </tr>
            }
          </tbody>
        </table>
      </section>
    } @else {
      @for (group of groups(); track group.key ?? '') {
        <section class="dt-report-viewer__block">
          @if (group.label !== null) {
            <h3 class="dt-report-viewer__caption">
              {{ group.label }}
              <span class="dt-report-viewer__group-total">{{ group.durationLabel }}</span>
            </h3>
          }

          <table class="dt-report-viewer__table">
            <thead>
              <tr>
                <th scope="col" i18n="@@report.column.date">Data</th>
                <th scope="col" i18n="@@report.column.ticket">Ticket</th>
                <th scope="col" i18n="@@report.column.category">Categoria</th>
                @if (showUserColumn()) {
                  <th scope="col" i18n="@@report.column.user">Pessoa</th>
                }
                <th scope="col" i18n="@@report.column.description">Descrição</th>
                <th scope="col" i18n="@@report.column.duration">Horas</th>
                @if (showValueColumn()) {
                  <th scope="col" i18n="@@report.column.value">Valor</th>
                }
              </tr>
            </thead>
            <tbody>
              @for (entry of group.entries; track $index) {
                <tr [class.dt-report-viewer__row--non-billable]="!entry.billable">
                  <td>{{ entry.workDate }}</td>
                  <td>
                    {{ entry.ticketKey ?? '—' }}
                    @if (entry.ticketTitle !== null) {
                      <small class="dt-report-viewer__hint">{{ entry.ticketTitle }}</small>
                    }
                  </td>
                  <td>{{ entry.categoryName ?? '—' }}</td>
                  @if (showUserColumn()) {
                    <td>{{ entry.userName ?? '—' }}</td>
                  }
                  <td class="dt-report-viewer__description">
                    {{ entry.description ?? '—' }}
                    @if (!entry.billable) {
                      <!-- CP-05: marcado, e fora do subtotal faturável. -->
                      <span
                        class="dt-report-viewer__flag"
                        i18n="@@report.entry.nonBillable"
                        i18n-pTooltip="@@report.entry.nonBillable.tooltip"
                        pTooltip="Esta linha não entra no total faturável."
                      >
                        não faturável
                      </span>
                    }
                  </td>
                  <td class="dt-report-viewer__numeric">{{ entry.durationLabel }}</td>
                  @if (showValueColumn()) {
                    <td class="dt-report-viewer__numeric">{{ entry.value | money: currency() }}</td>
                  }
                </tr>
              }
            </tbody>
            <tfoot>
              <tr>
                <td [attr.colspan]="labelColumns()" i18n="@@report.group.subtotal">Subtotal</td>
                <td class="dt-report-viewer__numeric">{{ group.durationLabel }}</td>
                @if (showValueColumn()) {
                  <td></td>
                }
              </tr>
              <tr>
                <td [attr.colspan]="labelColumns()" i18n="@@report.group.billableSubtotal">
                  Subtotal faturável
                </td>
                <td class="dt-report-viewer__numeric">
                  {{ group.totalBillableMinutes | duration }}
                </td>
                @if (showValueColumn()) {
                  <td></td>
                }
              </tr>
            </tfoot>
          </table>
        </section>
      }
    }

    <section class="dt-report-viewer__totals">
      <div>
        <span i18n="@@report.totals.duration">Total</span>
        <strong>{{ report().totals.durationLabel }}</strong>
      </div>
      <div>
        <span i18n="@@report.totals.billable">Faturáveis</span>
        <strong>{{ report().totals.billableMinutes | duration }}</strong>
      </div>
      <div>
        <span i18n="@@report.totals.nonBillable">Não faturáveis</span>
        <strong>{{ report().totals.nonBillableMinutes | duration }}</strong>
      </div>
      <div>
        <span i18n="@@report.totals.entries">Registros</span>
        <strong>{{ report().totals.entriesCount }}</strong>
      </div>
      <div>
        <span i18n="@@report.totals.days">Dias com registro</span>
        <strong>{{ report().totals.distinctDays }}</strong>
      </div>
      @if (showValueColumn()) {
        <div>
          <span i18n="@@report.totals.value">Valor total</span>
          <strong>{{ report().totals.totalValue | money: currency() }}</strong>
        </div>
      }
    </section>
  `,
  styleUrl: './report-viewer.component.scss',
})
export class ReportViewerComponent {
  readonly report = input.required<Report>();

  protected readonly productivity = computed<ProductivityReport | null>(() => {
    const report = this.report();
    return report.reportType === 'PRODUCTIVITY' ? report : null;
  });

  protected readonly groups = computed(() => {
    const report = this.report();
    return report.reportType === 'PRODUCTIVITY' ? [] : (report as GroupedReport).groups;
  });

  /**
   * §6: a coluna de pessoa é `auto` — aparece quando há mais de um autor no resultado.
   *
   * A decisão é tomada sobre o conteúdo recebido, não sobre o papel de quem olha: um relatório de
   * uma pessoa só com a coluna repetindo o mesmo nome em todas as linhas gasta largura sem informar.
   */
  protected readonly showUserColumn = computed(() => {
    const names = new Set<string>();
    for (const group of this.groups()) {
      for (const entry of group.entries) {
        if (entry.userName !== null) {
          names.add(entry.userName);
        }
      }
    }
    return names.size > 1;
  });

  /** CP-03: a coluna existe apenas quando o servidor enviou valores. */
  protected readonly showValueColumn = computed(() => {
    const report = this.report();
    if (report.reportType === 'PRODUCTIVITY') {
      return false;
    }
    return report.totals.totalValue !== null;
  });

  /**
   * Moeda do relatório, quando ele tem uma só.
   *
   * Detalhe de ticket e folha de horas não trazem bloco monetário no contrato do backend: o valor
   * por linha existe, a moeda não. `null` faz o pipe cair na moeda padrão — e é preferível a
   * inventar aqui uma moeda que o servidor não afirmou.
   *
   * O resumo por cliente usa a primeira moeda apenas para as linhas do detalhamento; os totais por
   * moeda são exibidos separadamente, sem conversão (CE-R-09).
   */
  protected readonly currency = computed(() => {
    const report = this.report();
    switch (report.reportType) {
      case 'CONTRACT_PERIOD':
        return report.financial?.currency ?? null;
      case 'CLIENT_SUMMARY':
        return report.totalsByCurrency[0]?.currency ?? null;
      default:
        return null;
    }
  });

  /** Colunas ocupadas pelo rótulo do subtotal, antes da coluna de duração. */
  protected readonly labelColumns = computed(() => (this.showUserColumn() ? 5 : 4));
}
