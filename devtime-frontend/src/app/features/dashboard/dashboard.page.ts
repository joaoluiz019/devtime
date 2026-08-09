import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SkeletonModule } from 'primeng/skeleton';
import { AuthStore } from '../../core/auth/auth.store';
import { messageForCode } from '../../core/error/error-messages';
import { BarChartComponent, BarPoint } from '../../shared/components/charts/bar-chart.component';
import {
  DonutChartComponent,
  DonutSlice,
} from '../../shared/components/charts/donut-chart.component';
import { DurationPipe } from '../../shared/pipes/duration.pipe';
import { ContractCardComponent } from './components/contract-card.component';
import { DashboardStore } from './data/dashboard.store';
import { DashboardPeriodType } from './data/dashboard.model';

/**
 * Painel — P09, layout L3 (T-010-16).
 *
 * A ordem das seções é normativa: estatísticas, contratos, alertas, gráficos, registros recentes e
 * tickets. Ela responde à pergunta do painel na sequência em que ela é feita — "quanto trabalhei",
 * "como estão os contratos", "o que exige ação".
 *
 * DB-05: bloco que falhou aparece assinalado; os demais são exibidos. Um painel que some inteiro
 * porque um gráfico falhou custa mais do que um painel com uma lacuna declarada.
 */
@Component({
  selector: 'dt-dashboard-page',
  imports: [
    RouterLink,
    BarChartComponent,
    ButtonModule,
    ContractCardComponent,
    DonutChartComponent,
    DurationPipe,
    MessageModule,
    SelectButtonModule,
    SkeletonModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [DashboardStore],
  template: `
    <header class="dt-dashboard__header">
      <div>
        <h1 class="dt-dashboard__title" i18n="@@dashboard.title">Dashboard</h1>
        <p class="dt-dashboard__subtitle">{{ greeting() }}</p>
      </div>

      <div class="dt-dashboard__periods">
        @for (option of periodOptions; track option.value) {
          <p-button
            [label]="option.label"
            severity="secondary"
            [outlined]="store.period() !== option.value"
            (onClick)="changePeriod(option.value)"
          />
        }
      </div>
    </header>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
      @if (store.failedBlocks().length > 0) {
        <!-- DB-05: a lacuna é declarada em vez de virar um zero silencioso. -->
        <p-message severity="warn" styleClass="w-full mb-3">
          <span i18n="@@dashboard.failedBlocks">
            Algumas seções não puderam ser carregadas: {{ store.failedBlocks().join(', ') }}.
          </span>
        </p-message>
      }
    </div>

    @if (store.loading()) {
      <p-skeleton height="8rem" styleClass="mb-3" />
      <p-skeleton height="14rem" />
    } @else if (store.dashboard(); as dashboard) {
      @if (dashboard.quickStats; as stats) {
        <section class="dt-dashboard__stats">
          <article class="dt-dashboard__stat">
            <span class="dt-dashboard__stat-label" i18n="@@dashboard.today">Hoje</span>
            <strong>{{ stats.todayMinutes | duration }}</strong>
          </article>
          <article class="dt-dashboard__stat">
            <span class="dt-dashboard__stat-label" i18n="@@dashboard.week">Esta semana</span>
            <strong>{{ stats.weekMinutes | duration }}</strong>
          </article>
          <article class="dt-dashboard__stat">
            <span class="dt-dashboard__stat-label" i18n="@@dashboard.period">No período</span>
            <strong>{{ stats.periodMinutes | duration }}</strong>
          </article>
          <article class="dt-dashboard__stat">
            <span class="dt-dashboard__stat-label" i18n="@@dashboard.timer">Cronômetro</span>
            <strong>{{ stats.activeTimerMinutes | duration }}</strong>
          </article>
        </section>
      }

      @if (store.contracts().length > 0) {
        <section class="dt-dashboard__section">
          <h2 class="dt-dashboard__section-title" i18n="@@dashboard.contracts">Contratos</h2>
          <div class="dt-dashboard__cards">
            @for (contract of store.contracts(); track contract.contractId) {
              <dt-contract-card [contract]="contract" />
            }
          </div>
        </section>
      } @else if (!store.isUserScope()) {
        <!-- Sem contratos: o caminho de saída é criar o primeiro, não um espaço vazio. -->
        <section class="dt-dashboard__empty">
          <h2 i18n="@@dashboard.empty.title">Nenhum contrato ativo</h2>
          <p i18n="@@dashboard.empty.text">
            Cadastre um contrato para acompanhar saldo, consumo e projeção por aqui.
          </p>
          <!-- FA-01: o estado vazio aponta para o onboarding, que cobre cliente, contrato e ticket. -->
          <p-button
            i18n-label="@@dashboard.empty.onboarding"
            label="Configurar em 5 minutos"
            routerLink="/onboarding"
          />
          <p-button
            i18n-label="@@dashboard.empty.action"
            label="Criar primeiro contrato"
            severity="secondary"
            [text]="true"
            routerLink="/contracts/new"
          />
        </section>
      }

      @if (store.alerts().length > 0) {
        <section class="dt-dashboard__section">
          <h2 class="dt-dashboard__section-title" i18n="@@dashboard.alerts">Alertas</h2>
          @for (alert of store.alerts(); track alert.type + alert.entityId) {
            <p-message
              [severity]="alertSeverity(alert.severity)"
              [text]="alert.message"
              styleClass="w-full mb-2"
            />
          }
        </section>
      }

      @if (dashboard.charts; as charts) {
        <section class="dt-dashboard__section">
          <h2 class="dt-dashboard__section-title" i18n="@@dashboard.dailyChart">Horas por dia</h2>
          <dt-bar-chart
            [points]="dailyPoints()"
            i18n-ariaLabel="@@dashboard.dailyChart.aria"
            ariaLabel="Minutos registrados por dia no período"
            i18n-caption="@@dashboard.dailyChart.caption"
            caption="A faixa escura é a parte faturável."
          />
        </section>

        <div class="dt-dashboard__split">
          <section class="dt-dashboard__section">
            <h2 class="dt-dashboard__section-title" i18n="@@dashboard.byClient">Por cliente</h2>
            <dt-donut-chart
              [slices]="clientSlices()"
              i18n-ariaLabel="@@dashboard.byClient.aria"
              ariaLabel="Distribuição de horas por cliente"
              (selected)="filterByClient($event)"
            />
          </section>

          <section class="dt-dashboard__section">
            <h2 class="dt-dashboard__section-title" i18n="@@dashboard.byCategory">Por categoria</h2>
            <dt-donut-chart
              [slices]="categorySlices()"
              i18n-ariaLabel="@@dashboard.byCategory.aria"
              ariaLabel="Distribuição de horas por categoria"
              (selected)="filterByCategory($event)"
            />
          </section>
        </div>
      }

      <div class="dt-dashboard__split">
        <section class="dt-dashboard__section">
          <h2 class="dt-dashboard__section-title" i18n="@@dashboard.recent">Registros recentes</h2>
          @if (store.recentWorkLogs().length === 0) {
            <p class="dt-dashboard__muted" i18n="@@dashboard.recent.empty">
              Nenhuma hora registrada no período.
            </p>
          } @else {
            <ul class="dt-dashboard__list" role="list">
              @for (entry of store.recentWorkLogs(); track entry.id) {
                <li>
                  <a [routerLink]="['/work-logs', entry.id, 'edit']">{{ entry.ticketKey }}</a>
                  <span>{{ entry.workDate }}</span>
                  <strong>{{ entry.durationLabel }}</strong>
                </li>
              }
            </ul>
          }
        </section>

        <section class="dt-dashboard__section">
          <h2 class="dt-dashboard__section-title" i18n="@@dashboard.tickets">
            Meus tickets em andamento
          </h2>
          @if (store.openTickets().length === 0) {
            <p class="dt-dashboard__muted" i18n="@@dashboard.tickets.empty">
              Nenhum ticket em andamento.
            </p>
          } @else {
            <ul class="dt-dashboard__list" role="list">
              @for (ticket of store.openTickets(); track ticket.id) {
                <li>
                  <a [routerLink]="['/tickets', ticket.id]">{{ ticket.key }}</a>
                  <span>{{ ticket.title }}</span>
                  <strong>{{ ticket.spentMinutes | duration }}</strong>
                </li>
              }
            </ul>
          }
        </section>
      </div>
    }
  `,
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage {
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly store = inject(DashboardStore);

  protected readonly periodOptions: readonly { label: string; value: DashboardPeriodType }[] = [
    { label: $localize`:@@dashboard.period.current:Período atual`, value: 'CURRENT_PERIOD' },
    { label: $localize`:@@dashboard.period.week:7 dias`, value: 'LAST_7_DAYS' },
    { label: $localize`:@@dashboard.period.month:30 dias`, value: 'LAST_30_DAYS' },
  ];

  protected readonly greeting = computed(() => {
    const name = this.authStore.displayName();
    return name === ''
      ? $localize`:@@dashboard.greeting.anonymous:Sua visão geral`
      : $localize`:@@dashboard.greeting:Olá, ${name}:name:`;
  });

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  /**
   * A série diária vem com zeros explícitos do servidor (ChartGapFiller).
   *
   * Omitir dias sem trabalho encolheria o eixo e faria uma semana de férias parecer uma semana cheia.
   */
  protected readonly dailyPoints = computed<readonly BarPoint[]>(() =>
    (this.store.dashboard()?.charts?.dailyMinutes ?? []).map((point) => ({
      label: point.date,
      value: point.netMinutes,
      highlight: point.billableMinutes,
    })),
  );

  protected readonly clientSlices = computed<readonly DonutSlice[]>(() =>
    toSlices(this.store.dashboard()?.charts?.byClient ?? []),
  );

  protected readonly categorySlices = computed<readonly DonutSlice[]>(() =>
    toSlices(this.store.dashboard()?.charts?.byCategory ?? []),
  );

  constructor() {
    void this.store.load();
  }

  protected alertSeverity(severity: string): 'error' | 'warn' | 'info' | 'secondary' {
    switch (severity) {
      case 'CRITICAL':
        return 'error';
      case 'WARNING':
        return 'warn';
      case 'INFO':
        return 'info';
      default:
        return 'secondary';
    }
  }

  protected async changePeriod(period: DashboardPeriodType): Promise<void> {
    await this.store.load(period);
  }

  /** Clicar numa fatia leva à lista de horas com o filtro correspondente (P09, interações). */
  protected async filterByClient(clientId: string): Promise<void> {
    await this.router.navigate(['/work-logs'], { queryParams: { clientId } });
  }

  protected async filterByCategory(categoryId: string): Promise<void> {
    await this.router.navigate(['/work-logs'], { queryParams: { categoryId } });
  }
}

function toSlices(
  slices: readonly { entityId?: string; label: string; color?: string; minutes: number }[],
): readonly DonutSlice[] {
  return slices.map((slice, index) => ({
    id: slice.entityId ?? `slice-${index}`,
    label: slice.label,
    value: slice.minutes,
    color: slice.color,
  }));
}
