import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { GROUPINGS_BY_TYPE, ReportGrouping, ReportType } from '../data/report.model';

/**
 * Escolha do agrupamento — `dt-grouping-selector` (T-012-23).
 *
 * §21.2 o classifica como componente compartilhado, mas ele vive na feature: o seu contrato depende
 * de `ReportType` e da tabela de compatibilidade de §6.3, e levá-lo para `shared` faria `shared`
 * depender de `features/reports`, que FR-004 proíbe. Nenhuma outra feature o consome hoje.
 *
 * §6.3: as opções são **filtradas** por compatibilidade, não desabilitadas. Diferente do seletor de
 * tipo, aqui não há nada a explicar — "agrupar por ticket" num relatório de um único ticket não é
 * uma permissão que falta, é uma combinação sem sentido.
 */
@Component({
  selector: 'dt-grouping-selector',
  imports: [FormsModule, SelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="dt-grouping__label" for="report-grouping" i18n="@@report.grouping.label">
      Agrupar por
    </label>
    <p-select
      inputId="report-grouping"
      [options]="options()"
      [ngModel]="value()"
      optionLabel="label"
      optionValue="value"
      i18n-ariaLabel="@@report.grouping.label"
      ariaLabel="Agrupar por"
      (onChange)="changed.emit($event.value)"
    />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
    }

    .dt-grouping__label {
      font-size: var(--dt-text-sm);
    }
  `,
})
export class GroupingSelectorComponent {
  readonly reportType = input.required<ReportType>();
  readonly value = input.required<ReportGrouping>();

  readonly changed = output<ReportGrouping>();

  protected readonly options = computed(() =>
    GROUPINGS_BY_TYPE[this.reportType()].map((grouping) => ({
      value: grouping,
      label: GROUPING_LABELS[grouping],
    })),
  );
}

const GROUPING_LABELS: Readonly<Record<ReportGrouping, string>> = {
  DATE: $localize`:@@report.grouping.date:Data`,
  WEEK: $localize`:@@report.grouping.week:Semana`,
  TICKET: $localize`:@@report.grouping.ticket:Ticket`,
  CATEGORY: $localize`:@@report.grouping.category:Categoria`,
  USER: $localize`:@@report.grouping.user:Pessoa`,
  TAG: $localize`:@@report.grouping.tag:Etiqueta`,
  NONE: $localize`:@@report.grouping.none:Sem agrupamento`,
};
