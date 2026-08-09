import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { ReportApi } from './report.api';
import {
  GroupedReport,
  MAX_RANGE_DAYS,
  rangeDays,
  Report,
  ReportCriteria,
  ReportGroup,
} from './report.model';

/**
 * Estado do relatório exibido em P24 (T-012-21).
 *
 * O store **não** guarda os critérios: eles vivem na tela, que é quem os monta a partir dos
 * seletores, e são passados a cada `load`. Duplicá-los aqui criaria duas verdades sobre o que está
 * sendo pedido, e a prévia com debounce de 500ms tornaria a divergência visível.
 */
@Injectable()
export class ReportStore {
  private readonly api = inject(ReportApi);

  private readonly _report = signal<Report | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly report = this._report.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  /** RN-706: é esta contagem que decide se a exportação será síncrona ou assíncrona. */
  readonly rowCount = computed(() => this._report()?.totals.entriesCount ?? 0);

  readonly isEmpty = computed(() => {
    const report = this._report();
    return report !== null && report.totals.entriesCount === 0;
  });

  /** Detalhamento agrupado; produtividade não tem grupos e devolve lista vazia. */
  readonly groups = computed<readonly ReportGroup[]>(() => {
    const report = this._report();
    if (report === null || report.reportType === 'PRODUCTIVITY') {
      return [];
    }
    return (report as GroupedReport).groups;
  });

  async load(criteria: ReportCriteria): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      this._report.set(await firstValueFrom(this.api.generate(criteria)));
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._report.set(null);
    } finally {
      this._loading.set(false);
    }
  }

  /** Limpa a prévia quando o recorte deixa de estar completo — exibir o anterior mentiria. */
  clear(): void {
    this._report.set(null);
    this._error.set(null);
  }
}

/**
 * O recorte está completo o bastante para pedir o relatório?
 *
 * Cada tipo exige o seu alvo (§8.1) e os três de intervalo livre exigem as duas datas. Pedir sem
 * isso produziria `DEVTIME-3003` ou `DEVTIME-2000` a cada tecla digitada, com o debounce de 500ms
 * transformando o preenchimento normal do formulário numa sequência de erros.
 */
export function isCriteriaComplete(criteria: ReportCriteria): boolean {
  const { from, to } = criteria.filters;
  const hasRange = from !== undefined && to !== undefined && to >= from;
  switch (criteria.reportType) {
    case 'CONTRACT_PERIOD':
      return criteria.contractPeriodId !== undefined;
    case 'TICKET_DETAIL':
      return criteria.ticketId !== undefined;
    case 'CLIENT_SUMMARY':
      return criteria.clientId !== undefined && hasRange;
    case 'TIMESHEET':
    case 'PRODUCTIVITY':
      return hasRange;
  }
}

/** RN-705 no cliente: o intervalo de mais de 366 dias é recusado antes da viagem. */
export function isRangeTooLong(criteria: ReportCriteria): boolean {
  const { from, to } = criteria.filters;
  if (from === undefined || to === undefined) {
    return false;
  }
  return rangeDays(from, to) > MAX_RANGE_DAYS;
}
