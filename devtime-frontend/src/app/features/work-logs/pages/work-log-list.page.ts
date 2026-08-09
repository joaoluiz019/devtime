import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PaginatorModule } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { AuthStore } from '../../../core/auth/auth.store';
import { messageForCode } from '../../../core/error/error-messages';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '../../../shared/models/page.model';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { WorkLogRowComponent } from '../components/work-log-row.component';
import { WorkLogListStore } from '../data/work-log-list.store';
import { WorkLogListQuery, WorkLogSource } from '../data/work-log.model';

/**
 * Lista de registros de horas — P21, layout L5 (T-008-33, T-008-35).
 *
 * LS-02 / SG-03: os totais vêm do endpoint de totais com os mesmos filtros, inclusive sob o escopo de
 * `MEMBER` — quem só enxerga os próprios registros vê o total dos próprios registros. Somar as linhas
 * da página daria um número diferente do que o servidor considera verdade.
 */
@Component({
  selector: 'dt-work-log-list-page',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    DurationPipe,
    InputTextModule,
    MessageModule,
    PaginatorModule,
    SelectModule,
    SkeletonModule,
    WorkLogRowComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [WorkLogListStore],
  template: `
    <header class="dt-worklogs__header">
      <div>
        <h1 class="dt-worklogs__title" i18n="@@workLogs.title">Registros de horas</h1>
        <p class="dt-worklogs__subtitle">{{ subtitle() }}</p>
      </div>
      <div class="dt-worklogs__header-actions">
        <p-button
          i18n-label="@@workLogs.calendar"
          label="Ver calendário"
          icon="pi pi-calendar"
          severity="secondary"
          [outlined]="true"
          routerLink="/work-logs/calendar"
        />
        @if (canCreate()) {
          <p-button
            i18n-label="@@workLogs.new"
            label="Lançar horas"
            icon="pi pi-plus"
            routerLink="/work-logs/new"
          />
        }
      </div>
    </header>

    <section class="dt-worklogs__filters">
      <input
        id="worklogs-search"
        type="search"
        pInputText
        i18n-placeholder="@@workLogs.search"
        placeholder="Buscar na descrição"
        i18n-aria-label="@@workLogs.search"
        aria-label="Buscar na descrição"
        [ngModel]="query().search ?? ''"
        (keyup.enter)="applySearch($any($event.target).value)"
        (search)="applySearch($any($event.target).value)"
      />

      <input
        id="worklogs-date-from"
        type="date"
        pInputText
        i18n-aria-label="@@workLogs.filter.from"
        aria-label="Data inicial"
        [ngModel]="query().dateFrom ?? ''"
        (change)="applyFilter('dateFrom', $any($event.target).value)"
      />

      <input
        id="worklogs-date-to"
        type="date"
        pInputText
        i18n-aria-label="@@workLogs.filter.to"
        aria-label="Data final"
        [ngModel]="query().dateTo ?? ''"
        (change)="applyFilter('dateTo', $any($event.target).value)"
      />

      <p-select
        [options]="billableOptions"
        [ngModel]="query().billable ?? null"
        optionLabel="label"
        optionValue="value"
        i18n-placeholder="@@workLogs.filter.billable"
        placeholder="Faturável"
        i18n-ariaLabel="@@workLogs.filter.billable"
        ariaLabel="Faturável"
        [showClear]="true"
        (onChange)="applyFilter('billable', $event.value)"
      />

      <p-button
        [label]="mineLabel()"
        severity="secondary"
        [outlined]="!isMineActive()"
        (onClick)="toggleMine()"
      />

      @if (hasFilters()) {
        <p-button
          i18n-label="@@clients.filter.clear"
          label="Limpar filtros"
          severity="secondary"
          [text]="true"
          (onClick)="clearFilters()"
        />
      }
    </section>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (store.loading()) {
      <p-skeleton height="12rem" />
    } @else if (store.isEmpty()) {
      <div class="dt-worklogs__empty">
        <h2 i18n="@@workLogs.empty.title">Nenhum registro encontrado</h2>
        <p i18n="@@workLogs.empty.text">
          Ajuste o período e os filtros, ou lance as horas trabalhadas.
        </p>
      </div>
    } @else {
      @if (store.totals(); as totals) {
        <!-- LS-02: total do conjunto filtrado, calculado pelo servidor. -->
        <section class="dt-worklogs__totals">
          <span>
            <strong>{{ totals.totalMinutes | duration }}</strong>
            <span i18n="@@workLogs.totals.total">no total</span>
          </span>
          <span>
            <strong>{{ totals.billableMinutes | duration }}</strong>
            <span i18n="@@workLogs.totals.billable">faturáveis</span>
          </span>
          <span>
            <strong>{{ totals.nonBillableMinutes | duration }}</strong>
            <span i18n="@@workLogs.totals.nonBillable">não faturáveis</span>
          </span>
          <span>
            <strong>{{ totals.entryCount }}</strong>
            <span i18n="@@workLogs.totals.entries">registros</span>
          </span>
        </section>
      }

      <div class="dt-worklogs__list">
        @for (entry of store.entries(); track entry.id) {
          <dt-work-log-row [entry]="entry" (removed)="remove($event)" />
        }
      </div>

      <p-paginator
        [first]="query().page * query().size"
        [rows]="query().size"
        [totalRecords]="store.total()"
        [rowsPerPageOptions]="pageSizeOptions"
        (onPageChange)="onPageChange($event)"
      />
    }
  `,
  styleUrl: './work-log-list.page.scss',
})
export class WorkLogListPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authStore = inject(AuthStore);

  protected readonly store = inject(WorkLogListStore);

  protected readonly pageSizeOptions = [...PAGE_SIZE_OPTIONS];

  protected readonly billableOptions = [
    { label: $localize`:@@workLogs.filter.billableYes:Faturável`, value: true },
    { label: $localize`:@@workLogs.filter.billableNo:Não faturável`, value: false },
  ];

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  protected readonly query = computed<WorkLogListQuery>(() => {
    const params = this.params();
    const billable = params.get('billable');
    return {
      userId: params.get('userId') ?? undefined,
      ticketId: params.get('ticketId') ?? undefined,
      contractId: params.get('contractId') ?? undefined,
      clientId: params.get('clientId') ?? undefined,
      categoryId: params.get('categoryId') ?? undefined,
      dateFrom: params.get('dateFrom') ?? undefined,
      dateTo: params.get('dateTo') ?? undefined,
      billable: billable === null ? undefined : billable === 'true',
      source: (params.get('source') as WorkLogSource | null) ?? undefined,
      search: params.get('search') ?? undefined,
      page: Number(params.get('page') ?? 0),
      size: Number(params.get('size') ?? DEFAULT_PAGE_SIZE),
      sort: params.get('sort') ?? 'startedAt,desc',
    };
  });

  protected readonly canCreate = computed(() => this.authStore.hasPermission('WORKLOG_CREATE'));

  protected readonly isMineActive = computed(
    () => this.query().userId === this.authStore.user()?.id,
  );

  protected readonly mineLabel = computed(() =>
    this.isMineActive()
      ? $localize`:@@workLogs.filter.everyone:Todos`
      : $localize`:@@workLogs.filter.mine:Só os meus`,
  );

  protected readonly hasFilters = computed(() => {
    const query = this.query();
    return (
      (query.search ?? '') !== '' ||
      query.dateFrom !== undefined ||
      query.dateTo !== undefined ||
      query.billable !== undefined ||
      query.userId !== undefined ||
      query.ticketId !== undefined ||
      query.contractId !== undefined
    );
  });

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly subtitle = computed(() => {
    const total = this.store.total();
    return total === 1
      ? $localize`:@@workLogs.subtitle.one:1 registro`
      : $localize`:@@workLogs.subtitle.many:${total}:count: registros`;
  });

  constructor() {
    effect(() => {
      const query = this.query();
      void this.store.load(query);
    });
  }

  protected applySearch(value: string): void {
    void this.updateParams({ search: value === '' ? null : value, page: 0 });
  }

  protected applyFilter(key: 'dateFrom' | 'dateTo' | 'billable', value: unknown): void {
    const normalized = value === null || value === undefined || value === '' ? null : String(value);
    void this.updateParams({ [key]: normalized, page: 0 });
  }

  protected toggleMine(): void {
    const userId = this.authStore.user()?.id ?? null;
    void this.updateParams({ userId: this.isMineActive() ? null : userId, page: 0 });
  }

  protected clearFilters(): void {
    void this.updateParams({
      search: null,
      dateFrom: null,
      dateTo: null,
      billable: null,
      userId: null,
      ticketId: null,
      contractId: null,
      clientId: null,
      categoryId: null,
      page: 0,
    });
  }

  protected onPageChange(event: { first?: number; rows?: number }): void {
    const size = event.rows ?? DEFAULT_PAGE_SIZE;
    void this.updateParams({ page: Math.floor((event.first ?? 0) / size), size });
  }

  protected async remove(id: string): Promise<void> {
    await this.store.remove(id, this.query());
  }

  private updateParams(params: Record<string, string | number | null>): Promise<boolean> {
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
    });
  }
}
