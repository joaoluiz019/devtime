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
import { TableModule } from 'primeng/table';
import { AuthStore } from '../../../core/auth/auth.store';
import { messageForCode } from '../../../core/error/error-messages';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '../../../shared/models/page.model';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { ContractStatusBadgeComponent } from '../components/contract-status-badge.component';
import { ContractListStore } from '../data/contract-list.store';
import { ContractListQuery, ContractStatus, ContractType } from '../data/contract.model';

/** Chip de filtro ativo (LS-01). */
interface FilterChip {
  readonly key: 'search' | 'status' | 'type';
  readonly label: string;
}

/**
 * Lista de contratos — P13, layout L4 (T-004-20).
 *
 * LS-03: busca, filtros, ordenação e página vivem na URL. O filtro por cliente também: é assim que o
 * detalhe do cliente consegue apontar para "os contratos deste cliente" com um link.
 */
@Component({
  selector: 'dt-contract-list-page',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    ContractStatusBadgeComponent,
    DurationPipe,
    InputTextModule,
    MessageModule,
    PaginatorModule,
    SelectModule,
    SkeletonModule,
    TableModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ContractListStore],
  template: `
    <header class="dt-contracts__header">
      <div>
        <h1 class="dt-contracts__title" i18n="@@contracts.title">Contratos</h1>
        <p class="dt-contracts__subtitle">{{ subtitle() }}</p>
      </div>
      @if (canCreate()) {
        <p-button
          i18n-label="@@contracts.new"
          label="Novo contrato"
          icon="pi pi-plus"
          routerLink="/contracts/new"
        />
      }
    </header>

    <section class="dt-contracts__filters">
      <input
        id="contracts-search"
        type="search"
        pInputText
        i18n-placeholder="@@contracts.search"
        placeholder="Buscar por código ou nome"
        i18n-aria-label="@@contracts.search"
        aria-label="Buscar por código ou nome"
        [ngModel]="query().search ?? ''"
        (keyup.enter)="applySearch($any($event.target).value)"
        (search)="applySearch($any($event.target).value)"
      />

      <p-select
        [options]="statusOptions"
        [ngModel]="query().status ?? null"
        optionLabel="label"
        optionValue="value"
        i18n-placeholder="@@contracts.filter.status"
        placeholder="Situação"
        i18n-ariaLabel="@@contracts.filter.status"
        ariaLabel="Situação"
        [showClear]="true"
        (onChange)="applyFilter('status', $event.value)"
      />

      <p-select
        [options]="typeOptions"
        [ngModel]="query().type ?? null"
        optionLabel="label"
        optionValue="value"
        i18n-placeholder="@@contracts.filter.type"
        placeholder="Tipo"
        i18n-ariaLabel="@@contracts.filter.type"
        ariaLabel="Tipo"
        [showClear]="true"
        (onChange)="applyFilter('type', $event.value)"
      />

      @if (chips().length > 0) {
        <p-button
          i18n-label="@@clients.filter.clear"
          label="Limpar filtros"
          severity="secondary"
          [text]="true"
          (onClick)="clearFilters()"
        />
      }
    </section>

    @if (chips().length > 0) {
      <ul class="dt-contracts__chips" role="list">
        @for (chip of chips(); track chip.key) {
          <li>
            <button type="button" class="dt-contracts__chip" (click)="removeFilter(chip.key)">
              <span>{{ chip.label }}</span>
              <i class="pi pi-times" aria-hidden="true"></i>
            </button>
          </li>
        }
      </ul>
    }

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (store.loading()) {
      <p-skeleton height="12rem" />
    } @else if (store.isEmpty()) {
      <div class="dt-contracts__empty">
        <h2 i18n="@@contracts.empty.title">Nenhum contrato encontrado</h2>
        <p i18n="@@contracts.empty.text">
          Ajuste os filtros ou crie um contrato para começar a registrar horas.
        </p>
      </div>
    } @else {
      <!-- O total de horas é o da página, e o rótulo diz isso: a API de listagem não devolve o
           somatório do conjunto filtrado, e apresentá-lo como tal seria um número errado. -->
      <p class="dt-contracts__totals">{{ totalsLabel() }}</p>

      <div class="dt-contracts__table">
        <p-table [value]="rows()" [rowHover]="true" [dataKey]="'id'">
          <ng-template #header>
            <tr>
              <th scope="col" i18n="@@contracts.column.code">Código</th>
              <th scope="col" i18n="@@contracts.column.name">Contrato</th>
              <th scope="col" i18n="@@contracts.column.client">Cliente</th>
              <th scope="col" i18n="@@contracts.column.hours">Horas/mês</th>
              <th scope="col" i18n="@@contracts.column.period">Período atual</th>
              <th scope="col" i18n="@@contracts.column.status">Situação</th>
            </tr>
          </ng-template>
          <ng-template #body let-contract>
            <tr>
              <td>
                <a [routerLink]="['/contracts', contract.id]">{{ contract.code }}</a>
              </td>
              <td>{{ contract.name }}</td>
              <td>
                <a [routerLink]="['/clients', contract.client.id]">{{ contract.client.name }}</a>
              </td>
              <td>
                @if (contract.monthlyMinutes) {
                  {{ contract.monthlyMinutes | duration }}
                } @else {
                  <!-- HOURLY_OPEN não tem teto mensal (INV-CTR-03). -->
                  <span i18n="@@contract.type.hourlyOpen">Por hora</span>
                }
              </td>
              <td>{{ contract.currentPeriod?.label ?? '—' }}</td>
              <td><dt-contract-status-badge [status]="contract.status" /></td>
            </tr>
          </ng-template>
        </p-table>
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
  styleUrl: './contract-list.page.scss',
})
export class ContractListPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authStore = inject(AuthStore);

  protected readonly store = inject(ContractListStore);

  protected readonly pageSizeOptions = [...PAGE_SIZE_OPTIONS];

  protected readonly statusOptions = [
    { label: $localize`:@@contract.status.draft:Rascunho`, value: 'DRAFT' },
    { label: $localize`:@@contract.status.active:Ativo`, value: 'ACTIVE' },
    { label: $localize`:@@contract.status.suspended:Suspenso`, value: 'SUSPENDED' },
    { label: $localize`:@@contract.status.ended:Encerrado`, value: 'ENDED' },
    { label: $localize`:@@contract.status.cancelled:Cancelado`, value: 'CANCELLED' },
  ];

  protected readonly typeOptions = [
    { label: $localize`:@@contract.type.monthly:Horas mensais`, value: 'MONTHLY_HOURS' },
    { label: $localize`:@@contract.type.hourlyOpen:Por hora`, value: 'HOURLY_OPEN' },
  ];

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  protected readonly query = computed<ContractListQuery>(() => {
    const params = this.params();
    return {
      clientId: params.get('clientId') ?? undefined,
      status: (params.get('status') as ContractStatus | null) ?? undefined,
      type: (params.get('type') as ContractType | null) ?? undefined,
      search: params.get('search') ?? undefined,
      page: Number(params.get('page') ?? 0),
      size: Number(params.get('size') ?? DEFAULT_PAGE_SIZE),
      sort: params.get('sort') ?? 'code,asc',
    };
  });

  protected readonly canCreate = computed(() => this.authStore.hasPermission('CONTRACT_CREATE'));

  protected readonly rows = computed(() => [...this.store.contracts()]);

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly subtitle = computed(() => {
    const total = this.store.total();
    return total === 1
      ? $localize`:@@contracts.subtitle.one:1 contrato`
      : $localize`:@@contracts.subtitle.many:${total}:count: contratos`;
  });

  protected readonly totalsLabel = computed(
    () =>
      $localize`:@@contracts.totals:${this.store.total()}:total: no total · ${this.store.pageContractedMinutes()}:minutes: minutos contratados nesta página`,
  );

  protected readonly chips = computed<readonly FilterChip[]>(() => {
    const query = this.query();
    const chips: FilterChip[] = [];
    if (query.search !== undefined && query.search !== '') {
      chips.push({
        key: 'search',
        label: $localize`:@@clients.chip.search:Busca: ${query.search}:term:`,
      });
    }
    if (query.status !== undefined) {
      chips.push({ key: 'status', label: this.optionLabel(this.statusOptions, query.status) });
    }
    if (query.type !== undefined) {
      chips.push({ key: 'type', label: this.optionLabel(this.typeOptions, query.type) });
    }
    return chips;
  });

  constructor() {
    effect(() => {
      const query = this.query();
      void this.store.load(query);
    });
  }

  private optionLabel(options: readonly { label: string; value: string }[], value: string): string {
    return options.find((option) => option.value === value)?.label ?? value;
  }

  protected applySearch(value: string): void {
    void this.updateParams({ search: value === '' ? null : value, page: 0 });
  }

  protected applyFilter(key: 'status' | 'type', value: unknown): void {
    void this.updateParams({
      [key]: value === null || value === undefined ? null : String(value),
      page: 0,
    });
  }

  protected removeFilter(key: FilterChip['key']): void {
    void this.updateParams({ [key]: null, page: 0 });
  }

  protected clearFilters(): void {
    void this.updateParams({ search: null, status: null, type: null, clientId: null, page: 0 });
  }

  protected onPageChange(event: { first?: number; rows?: number }): void {
    const size = event.rows ?? DEFAULT_PAGE_SIZE;
    void this.updateParams({ page: Math.floor((event.first ?? 0) / size), size });
  }

  private updateParams(params: Record<string, string | number | null>): Promise<boolean> {
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
    });
  }
}
