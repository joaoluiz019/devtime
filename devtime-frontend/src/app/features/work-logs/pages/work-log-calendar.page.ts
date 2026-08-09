import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { AuthStore } from '../../../core/auth/auth.store';
import { messageForCode } from '../../../core/error/error-messages';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { CalendarCell, WorkLogCalendarStore } from '../data/work-log-calendar.store';

const WEEKDAYS: readonly string[] = [
  $localize`:@@calendar.mon:seg`,
  $localize`:@@calendar.tue:ter`,
  $localize`:@@calendar.wed:qua`,
  $localize`:@@calendar.thu:qui`,
  $localize`:@@calendar.fri:sex`,
  $localize`:@@calendar.sat:sáb`,
  $localize`:@@calendar.sun:dom`,
];

/**
 * Calendário de horas — P22, layout L4 (T-008-34).
 *
 * Responde "em que dias eu trabalhei e quanto", que é a pergunta que a lista não responde bem: uma
 * tabela ordenada por data esconde os buracos, e é o buraco — o dia esquecido — que importa achar
 * antes do fechamento do período.
 *
 * Cada dia é um link para a lista filtrada naquele dia: o calendário mostra o total, a lista mostra
 * de onde ele veio.
 */
@Component({
  selector: 'dt-work-log-calendar-page',
  imports: [RouterLink, ButtonModule, DurationPipe, MessageModule, SkeletonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [WorkLogCalendarStore],
  template: `
    <header class="dt-calendar__header">
      <div>
        <h1 class="dt-calendar__title" i18n="@@workLogs.calendar.title">Calendário de horas</h1>
        <p class="dt-calendar__subtitle">
          {{ monthLabel() }} · {{ store.totalMinutes() | duration }}
        </p>
      </div>
      <div class="dt-calendar__header-actions">
        <p-button
          i18n-label="@@workLogs.list"
          label="Ver lista"
          icon="pi pi-list"
          severity="secondary"
          [outlined]="true"
          routerLink="/work-logs"
        />
      </div>
    </header>

    <nav class="dt-calendar__nav" i18n-aria-label="@@calendar.nav" aria-label="Navegação de mês">
      <p-button
        icon="pi pi-chevron-left"
        severity="secondary"
        [text]="true"
        i18n-ariaLabel="@@calendar.previous"
        ariaLabel="Mês anterior"
        (onClick)="shift(-1)"
      />
      <span class="dt-calendar__month">{{ monthLabel() }}</span>
      <p-button
        icon="pi pi-chevron-right"
        severity="secondary"
        [text]="true"
        i18n-ariaLabel="@@calendar.next"
        ariaLabel="Próximo mês"
        (onClick)="shift(1)"
      />
      <p-button
        i18n-label="@@calendar.today"
        label="Hoje"
        severity="secondary"
        [text]="true"
        (onClick)="goToday()"
      />
      <p-button
        [label]="mineLabel()"
        severity="secondary"
        [outlined]="!onlyMine()"
        (onClick)="toggleMine()"
      />
    </nav>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (store.loading()) {
      <p-skeleton height="20rem" />
    } @else {
      <div class="dt-calendar__grid" role="grid">
        @for (weekday of weekdays; track weekday) {
          <div class="dt-calendar__weekday" role="columnheader">{{ weekday }}</div>
        }

        @for (cell of store.cells(); track cell.date) {
          <a
            class="dt-calendar__cell"
            [class.dt-calendar__cell--outside]="cell.outside"
            [class.dt-calendar__cell--today]="cell.isToday"
            [class.dt-calendar__cell--empty]="cell.entryCount === 0"
            [routerLink]="['/work-logs']"
            [queryParams]="{ dateFrom: cell.date, dateTo: cell.date }"
            [attr.aria-label]="cellLabel(cell)"
          >
            <span class="dt-calendar__day">{{ cell.day }}</span>
            @if (cell.entryCount > 0) {
              <span class="dt-calendar__total">{{ cell.totalMinutes | duration }}</span>
              <span class="dt-calendar__count">{{ cell.entryCount }}</span>
            }
          </a>
        }
      </div>

      <p class="dt-calendar__legend" i18n="@@calendar.legend">
        Cada dia leva à lista de registros daquela data.
      </p>
    }
  `,
  styleUrl: './work-log-calendar.page.scss',
})
export class WorkLogCalendarPage {
  private readonly authStore = inject(AuthStore);

  protected readonly store = inject(WorkLogCalendarStore);

  protected readonly weekdays = WEEKDAYS;

  private readonly _onlyMine = signal(true);
  private readonly _month = signal(new Date());

  protected readonly onlyMine = this._onlyMine.asReadonly();

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly monthLabel = computed(() =>
    this.store.month().toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' }),
  );

  protected readonly mineLabel = computed(() =>
    this._onlyMine()
      ? $localize`:@@workLogs.filter.everyone:Todos`
      : $localize`:@@workLogs.filter.mine:Só os meus`,
  );

  constructor() {
    // O usuário da sessão pode chegar depois da criação da tela (restauração pelo cookie), e o
    // calendário nasce filtrado pela própria pessoa: carregar antes disso pediria o mês da
    // organização inteira e depois o corrigiria, com duas consultas e um piscar de conteúdo.
    effect(() => {
      const userId = this.authStore.user()?.id;
      const scope = this._onlyMine() ? userId : undefined;
      void this.store.load(this._month(), scope);
    });
  }

  /** A11Y: o rótulo diz a data e o total; o número solto na célula não basta para leitor de tela. */
  protected cellLabel(cell: CalendarCell): string {
    return cell.entryCount === 0
      ? $localize`:@@calendar.cell.empty:${cell.date}:date:, sem registros`
      : $localize`:@@calendar.cell:${cell.date}:date:, ${cell.totalMinutes}:minutes: minutos em ${cell.entryCount}:count: registros`;
  }

  protected shift(months: number): void {
    const month = new Date(this._month());
    month.setMonth(month.getMonth() + months);
    this._month.set(month);
  }

  protected goToday(): void {
    this._month.set(new Date());
  }

  /**
   * O escopo padrão é o próprio usuário.
   *
   * O calendário existe para conferir o próprio trabalho antes do fechamento, e `MEMBER` só enxerga
   * os próprios registros de qualquer forma (§9 de `permissions.md`).
   */
  protected toggleMine(): void {
    this._onlyMine.set(!this._onlyMine());
  }
}
