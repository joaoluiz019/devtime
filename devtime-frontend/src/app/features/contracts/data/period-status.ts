import { PeriodStatus } from '../../../shared/models/balance.model';

/**
 * Rótulos e ícones dos estados do período (`state-machines.md` §4.6, `components.md` §6.8).
 *
 * SB-02: o rótulo é traduzido, nunca o valor do enum. SB-01/DS-05: o estado sempre aparece com
 * texto, e o ícone acompanha — cor sozinha não comunica.
 */
export function periodStatusLabel(status: PeriodStatus): string {
  switch (status) {
    case 'SCHEDULED':
      return $localize`:@@period.status.scheduled:Programado`;
    case 'OPEN':
      return $localize`:@@period.status.open:Aberto`;
    case 'CLOSING':
      return $localize`:@@period.status.closing:Fechando`;
    case 'CLOSED':
      return $localize`:@@period.status.closed:Fechado`;
    case 'REOPENED':
      return $localize`:@@period.status.reopened:Reaberto`;
  }
}

/** Ícones fixados pelo mapeamento de `dt-status-badge` (components.md §6.8). */
export function periodStatusIcon(status: PeriodStatus): string {
  switch (status) {
    case 'SCHEDULED':
      return 'pi-calendar';
    case 'OPEN':
      return 'pi-unlock';
    case 'CLOSING':
      return 'pi-spinner';
    case 'CLOSED':
      return 'pi-lock';
    case 'REOPENED':
      return 'pi-replay';
  }
}
