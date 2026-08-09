import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { WorkLogApi } from './work-log.api';
import { WorkLogCalendarDay } from './work-log.model';

/** Célula do calendário: um dia do mês exibido, com ou sem registros. */
export interface CalendarCell {
  readonly date: string;
  readonly day: number;
  readonly totalMinutes: number;
  readonly billableMinutes: number;
  readonly entryCount: number;
  /** Dias de preenchimento antes do dia 1 e depois do último; existem só para alinhar a grade. */
  readonly outside: boolean;
  readonly isToday: boolean;
}

/**
 * Estado do calendário de horas (P22, T-008-34).
 *
 * O servidor devolve **apenas os dias com registro**; a grade precisa de todos. O preenchimento é
 * feito aqui, e não no backend, porque é uma necessidade de apresentação: quem consome os totais para
 * relatório não quer trinta dias zerados no meio.
 */
@Injectable()
export class WorkLogCalendarStore {
  private readonly api = inject(WorkLogApi);

  private readonly _days = signal<readonly WorkLogCalendarDay[]>([]);
  private readonly _month = signal(startOfMonth(new Date()));
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly month = this._month.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly totalMinutes = computed(() =>
    this._days().reduce((total, day) => total + day.totalMinutes, 0),
  );

  readonly billableMinutes = computed(() =>
    this._days().reduce((total, day) => total + day.billableMinutes, 0),
  );

  /**
   * Grade do mês, alinhada por semana começando na segunda-feira.
   *
   * A semana brasileira de trabalho começa na segunda; iniciar no domingo empurraria o fim de semana
   * para as pontas opostas da linha e dificultaria ler "quanto trabalhei nesta semana".
   */
  readonly cells = computed<readonly CalendarCell[]>(() => {
    const month = this._month();
    const byDate = new Map(this._days().map((day) => [day.date, day]));
    const today = isoDate(new Date());

    const first = startOfMonth(month);
    const leading = (first.getDay() + 6) % 7;
    const start = new Date(first);
    start.setDate(start.getDate() - leading);

    const cells: CalendarCell[] = [];
    for (let index = 0; index < 42; index += 1) {
      const date = new Date(start);
      date.setDate(start.getDate() + index);
      const iso = isoDate(date);
      const found = byDate.get(iso);
      cells.push({
        date: iso,
        day: date.getDate(),
        totalMinutes: found?.totalMinutes ?? 0,
        billableMinutes: found?.billableMinutes ?? 0,
        entryCount: found?.entryCount ?? 0,
        outside: date.getMonth() !== month.getMonth(),
        isToday: iso === today,
      });
    }
    // A sexta linha só entra quando o mês realmente a ocupa; senão a grade ganharia uma semana vazia.
    return cells[35]?.outside === true && cells[41]?.outside === true ? cells.slice(0, 35) : cells;
  });

  async load(month: Date, userId?: string): Promise<void> {
    const first = startOfMonth(month);
    this._month.set(first);
    this._loading.set(true);
    this._error.set(null);
    try {
      const calendar = await firstValueFrom(
        this.api.calendar(isoDate(first), isoDate(endOfMonth(first)), userId),
      );
      this._days.set(calendar.days);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._days.set([]);
    } finally {
      this._loading.set(false);
    }
  }
}

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function endOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0);
}

/**
 * Data no formato do backend, montada a partir dos componentes locais.
 *
 * `toISOString()` converteria para UTC e, a oeste de Greenwich, devolveria o dia anterior — que é
 * exatamente o erro que RN-108 evita ao definir `workDate` como data local do tenant.
 */
export function isoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
