import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ReportCriteria } from '../data/report.model';

/**
 * Relatório sem nenhum registro — `dt-empty-report` (T-012-29).
 *
 * A mensagem é **explícita sobre o recorte**: "nenhum registro" sozinho deixa a dúvida entre não
 * haver horas lançadas e o filtro estar errado — e as duas conclusões levam a ações opostas. Quem
 * acha que não há horas vai cobrar a equipe; quem entende que filtrou demais corrige o formulário.
 */
@Component({
  selector: 'dt-empty-report',
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-empty-report">
      <i class="pi pi-inbox" aria-hidden="true"></i>
      <h2 i18n="@@report.empty.title">Nenhum registro neste recorte</h2>
      <p>{{ description() }}</p>
      @if (activeFilters() > 0) {
        <p class="dt-empty-report__hint" i18n="@@report.empty.filters">
          Há {{ activeFilters() }} filtro(s) aplicado(s) além do recorte principal. Remova-os para
          ampliar o resultado.
        </p>
      }
    </div>
  `,
  styles: `
    .dt-empty-report {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dt-space-2);
      padding: var(--dt-space-8) var(--dt-space-4);
      border: 1px dashed var(--dt-border);
      border-radius: var(--dt-radius-md);
      text-align: center;
    }

    .dt-empty-report i {
      font-size: var(--dt-text-3xl);
      color: var(--dt-text-secondary);
    }

    .dt-empty-report h2 {
      margin: 0;
      font-size: var(--dt-text-lg);
    }

    .dt-empty-report p {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-sm);
    }

    .dt-empty-report__hint {
      max-width: 32rem;
    }
  `,
})
export class EmptyReportComponent {
  readonly criteria = input.required<ReportCriteria>();

  protected readonly description = computed(() => {
    const { from, to } = this.criteria().filters;
    if (from !== undefined && to !== undefined) {
      return $localize`:@@report.empty.range:Não há horas lançadas entre ${from}:from: e ${to}:to: com estes filtros.`;
    }
    return $localize`:@@report.empty.scope:Não há horas lançadas neste recorte com os filtros aplicados.`;
  });

  /** Conta apenas os filtros acessórios: o recorte principal é obrigatório e não é "um filtro". */
  protected readonly activeFilters = computed(() => {
    const filters = this.criteria().filters;
    const lists = [filters.categoryIds, filters.tagIds, filters.userIds, filters.contractIds];
    const listCount = lists.filter((list) => (list?.length ?? 0) > 0).length;
    const billableCount = filters.billable === undefined ? 0 : 1;
    const nonBillableCount = filters.includeNonBillable === false ? 1 : 0;
    return listCount + billableCount + nonBillableCount;
  });
}
